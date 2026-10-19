import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from publish_google_play import publish


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.bundle = Path(self.temp.name) / 'test.aab'
        self.bundle.write_bytes(b'synthetic bundle')
        self.digest = hashlib.sha256(self.bundle.read_bytes()).hexdigest()
        self.session = Mock()

    def respond(self, *responses):
        self.session.request.side_effect = [
            Mock(ok=True, content=b'json', json=Mock(return_value=value)) for value in responses
        ]

    def test_uploads_new_bundle_and_replaces_pending_review(self):
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta'}]}, {},
                     {'versionCode': 134}, {}, {})
        self.assertEqual(publish(self.session, self.bundle, 'beta', 'completed', 'v1.5.1-dev.1'), '134')
        calls = self.session.request.call_args_list
        self.assertEqual(calls[4].kwargs['json']['releases'][0]['versionCodes'], ['134'])
        self.assertEqual(calls[5].kwargs['params'], {'changesInReviewBehavior': 'CANCEL_IN_REVIEW_AND_SUBMIT'})
        self.assertFalse(any(call.args[0] == 'DELETE' for call in calls))

    def test_promotes_uploaded_bundle_to_production_without_reupload(self):
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta'}, {'track': 'production'}]},
                     {'bundles': [{'versionCode': 133, 'sha256': self.digest}]}, {}, {})
        self.assertEqual(publish(self.session, self.bundle, 'production', 'completed', 'v1.5.0'), '133')
        calls = self.session.request.call_args_list
        self.assertFalse(any('/upload/' in call.args[1] for call in calls))
        self.assertEqual(calls[3].kwargs['json']['track'], 'production')
        self.assertEqual(calls[3].kwargs['json']['releases'][0]['versionCodes'], ['133'])

    def test_already_published_release_is_idempotent(self):
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'production', 'releases': [
            {'versionCodes': ['133'], 'status': 'completed'}]}]},
            {'bundles': [{'versionCode': 133, 'sha256': self.digest}]}, {})
        self.assertEqual(publish(self.session, self.bundle, 'production', 'completed', 'v1.5.0'), '133')
        calls = self.session.request.call_args_list
        self.assertEqual(calls[-1].args[0], 'DELETE')
        self.assertFalse(any(call.args[0] == 'PUT' or call.args[1].endswith(':commit') for call in calls))

    def test_prereleases_cannot_reach_production(self):
        with self.assertRaises(ValueError):
            publish(self.session, self.bundle, 'production', 'completed', 'v1.5.1-dev.1')
        self.session.request.assert_not_called()

    def test_invalid_track_never_contacts_google(self):
        with self.assertRaises(ValueError):
            publish(self.session, self.bundle, 'wear:production', 'completed', 'v1.5.0')
        self.session.request.assert_not_called()

    def test_missing_track_discards_edit_without_upload(self):
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta'}]}, {})
        with self.assertRaises(ValueError):
            publish(self.session, self.bundle, 'production', 'draft', 'v1.5.0')
        self.assertEqual(self.session.request.call_args_list[-1].args[0], 'DELETE')
        self.assertEqual(len(self.session.request.call_args_list), 3)

    def test_rejected_upload_does_not_commit(self):
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta'}]}, {})
        failed = Mock(ok=False, status_code=403, json=Mock(return_value={'error': {'message': 'Permission denied'}}))
        self.session.request.side_effect = [
            *self.session.request.side_effect, failed,
            Mock(ok=True, content=b'', json=Mock(return_value={})),
        ]
        with self.assertRaisesRegex(RuntimeError, 'Permission denied'):
            publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0')
        self.assertFalse(any(call.args[1].endswith(':commit') for call in self.session.request.call_args_list))
        self.assertEqual(self.session.request.call_args_list[-1].args[0], 'DELETE')

    def test_invalid_status_never_contacts_google(self):
        with self.assertRaises(ValueError):
            publish(self.session, self.bundle, 'beta', 'unexpected', 'v1.5.0')
        self.session.request.assert_not_called()

    def metadata(self, locale='en-US'):
        root = Path(self.temp.name) / 'metadata'
        folder = root / locale
        folder.mkdir(parents=True)
        (folder / 'title.txt').write_text('Title')
        (folder / 'short_description.txt').write_text('Short')
        (folder / 'full_description.txt').write_text('Full')
        return root

    def test_invalid_listing_rejected_before_network(self):
        root = self.metadata()
        (root / 'en-US' / 'title.txt').write_text('x' * 31)
        with self.assertRaisesRegex(ValueError, '30-character'):
            publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0', root, ['en-US'])
        self.session.request.assert_not_called()

    def test_listing_update_merges_video_and_commits_even_if_release_same(self):
        root = self.metadata()
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta', 'releases': [
            {'versionCodes': ['134'], 'status': 'draft'}]}]},
            {'bundles': [{'versionCode': 134, 'sha256': self.digest}]},
            {'listings': [{'language': 'en-US', 'title': 'Old', 'shortDescription': 'Short',
                           'fullDescription': 'Full', 'video': 'https://video', 'other': 'keep'}]},
            {}, {})
        self.assertEqual(publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0', root,
                                 ['en-US']), '134')
        calls = self.session.request.call_args_list
        listing_put = next(call for call in calls if call.args[0] == 'PUT' and '/listings/' in call.args[1])
        self.assertEqual(listing_put.kwargs['json'], {
            'language': 'en-US', 'title': 'Title', 'shortDescription': 'Short',
            'fullDescription': 'Full', 'video': 'https://video', 'other': 'keep'})
        self.assertTrue(any(call.args[1].endswith(':commit') for call in calls))

    def test_unchanged_listing_and_release_discards_edit(self):
        root = self.metadata()
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta', 'releases': [
            {'versionCodes': ['134'], 'status': 'draft'}]}]},
            {'bundles': [{'versionCode': 134, 'sha256': self.digest}]},
            {'listings': [{'language': 'en-US', 'title': 'Title', 'shortDescription': 'Short',
                           'fullDescription': 'Full', 'video': 'https://video'}]}, {})
        publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0', root, ['en-US'])
        calls = self.session.request.call_args_list
        self.assertEqual(calls[-1].args[0], 'DELETE')
        self.assertFalse(any(call.args[0] == 'PUT' or call.args[1].endswith(':commit') for call in calls))

    def test_all_selected_locales_validate_before_network(self):
        root = self.metadata()
        for content in (None, '', 'x' * 81):
            with self.subTest(content=content):
                folder = root / 'it-IT'
                folder.mkdir(exist_ok=True)
                (folder / 'title.txt').write_text('Titolo')
                (folder / 'full_description.txt').write_text('Descrizione')
                short = folder / 'short_description.txt'
                if content is not None:
                    short.write_text(content)
                with self.assertRaises(ValueError):
                    publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0',
                            root, ['en-US', 'it-IT'])
                self.session.request.assert_not_called()

    def test_creates_selected_locale_without_touching_other_languages(self):
        root = self.metadata('it-IT')
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta', 'releases': [
            {'versionCodes': ['134'], 'status': 'draft'}]}]},
            {'bundles': [{'versionCode': 134, 'sha256': self.digest}]},
            {'listings': [{'language': 'de-DE', 'title': 'Existing German listing'}]}, {}, {})
        publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0', root, ['it-IT'])
        writes = [call for call in self.session.request.call_args_list if call.args[0] == 'PUT']
        self.assertEqual(len(writes), 1)
        self.assertTrue(writes[0].args[1].endswith('/listings/it-IT'))
        self.assertEqual(writes[0].kwargs['json']['language'], 'it-IT')

    def test_listing_failure_discards_edit(self):
        root = self.metadata()
        self.respond({'id': 'edit'}, {'tracks': [{'track': 'beta'}]},
                     {'bundles': [{'versionCode': 134, 'sha256': self.digest}]}, {})
        failed = Mock(ok=False, status_code=500,
                      json=Mock(return_value={'error': {'message': 'Listing failed'}}))
        self.session.request.side_effect = [*self.session.request.side_effect, failed,
            Mock(ok=True, content=b'', json=Mock(return_value={}))]
        with self.assertRaisesRegex(RuntimeError, 'Listing failed'):
            publish(self.session, self.bundle, 'beta', 'draft', 'v1.5.0', root, ['en-US'])
        calls = self.session.request.call_args_list
        self.assertEqual(calls[-1].args[0], 'DELETE')
        self.assertFalse(any(call.args[1].endswith(':commit') for call in calls))


if __name__ == '__main__':
    unittest.main()
