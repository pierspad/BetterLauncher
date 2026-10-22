const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

function fixture(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'betterlauncher-version-'));
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    fs.mkdirSync(path.join(root, 'scripts'));
    fs.copyFileSync(path.join(__dirname, 'bump-version.js'), path.join(root, 'scripts/bump-version.js'));
    fs.writeFileSync(path.join(root, 'gradle.properties'), 'versionName=1.5.0\nversionCode=133\n');
    return {
        root,
        git: (...args) => execFileSync('git', args, { cwd: root, stdio: 'pipe' }),
        bump: (version, env = {}) => execFileSync(process.execPath, ['scripts/bump-version.js', version], { cwd: root, stdio: 'pipe', env: { ...process.env, ...env } }),
        contents: () => fs.readFileSync(path.join(root, 'gradle.properties'), 'utf8'),
    };
}

test('source archives retain a reproducible committed version counter', t => {
    const repo = fixture(t);
    repo.bump('1.5.1');
    assert.match(repo.contents(), /versionName=1.5.1\nversionCode=134\n/);
});

test('stable releases advance beyond dev versionCodes from other branches', t => {
    const repo = fixture(t);
    repo.git('init', '--quiet');
    repo.git('config', 'user.name', 'Regression Test');
    repo.git('config', 'user.email', 'regression@example.invalid');
    repo.git('add', 'gradle.properties');
    repo.git('commit', '-qm', 'stable');
    repo.git('tag', 'v1.5.0');
    fs.writeFileSync(path.join(repo.root, 'gradle.properties'), 'versionName=1.5.1-dev.1\nversionCode=140\n');
    repo.git('add', 'gradle.properties');
    repo.git('commit', '-qm', 'dev');
    repo.git('tag', 'v1.5.1-dev.1');
    repo.git('checkout', 'v1.5.0', '--', 'gradle.properties');
    repo.bump('1.5.1');
    assert.match(repo.contents(), /versionCode=141\n/);
});

test('invalid versions cannot modify gradle.properties', t => {
    const repo = fixture(t);
    const before = repo.contents();
    assert.throws(() => repo.bump('invalid\nversionCode=1'));
    assert.equal(repo.contents(), before);
});

test('prepared release exposes its version to the automatic Play publish job', t => {
    const repo = fixture(t);
    const output = path.join(repo.root, 'release-output');
    repo.bump('1.5.3', { GITHUB_OUTPUT: output });
    assert.equal(fs.readFileSync(output, 'utf8'), 'release_version=1.5.3\n');
    assert.match(repo.contents(), /versionName=1.5.3\n/);
});
