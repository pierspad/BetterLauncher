# Release automatiche su Google Play

L’automazione usa questi canali:

| Branch | Release GitHub | Google Play |
| --- | --- | --- |
| `main` | stabile, `vX.Y.Z` | produzione, `production` |
| `dev` | prerelease, `vX.Y.Z-dev.N` | test aperti, `beta` |

Un push viene verificato dalla CI. Semantic-release crea una versione quando i
commit lo richiedono (`fix:`, `feat:`, ecc.); un push di sola documentazione o CI
non genera automaticamente una nuova versione dell’app. L’invio Play segue la
pubblicazione del relativo APK/AAB firmato su GitHub.

## Accesso Google

L’app deve già esistere in Play Console, con un primo APK/AAB caricato e le
informazioni richieste da Google completate. L’API non crea l’app e non accetta
accordi o dichiarazioni al posto del titolare.

1. In [Google Cloud Console](https://console.cloud.google.com/), abilita la
   [Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).
2. Crea un account di servizio in **IAM e amministrazione → Account di servizio**.
   Non serve il ruolo Owner del progetto Cloud.
3. In **Play Console → Utenti e autorizzazioni**, invita l’email dell’account di
   servizio e concedi accesso a BetterLauncher. Servono visualizzazione delle
   informazioni dell’app, rilascio sui canali di test e **rilascio in produzione**.
   Il solo permesso per i test non basta per `main`.
4. Genera una chiave JSON e salvala nel secret GitHub
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`. Non inserirla nel repository.

```sh
gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON --repo pierspad/BetterLauncher < /percorso/privato/account-di-servizio.json
```

La firma usa i secret `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e
`KEY_PASSWORD`. Il certificato deve corrispondere alla chiave di caricamento
accettata da Play; può essere diverso dalla chiave con cui Google firma gli APK
consegnati agli utenti.

## Attivazione e comportamento

La variabile GitHub `GOOGLE_PLAY_AUTO_PUBLISH=true` abilita l’invio automatico.
Il canale viene scelto dal branch, senza una variabile globale di canale che
possa mescolare produzione e test. Lo stato richiesto è `completed`.

Per scelta del maintainer, se ci sono modifiche già in revisione, l’automazione
annulla quella revisione e reinvia l’insieme delle modifiche con la nuova versione
(`CANCEL_IN_REVIEW_AND_SUBMIT`). Restano applicabili i tempi di revisione e le
impostazioni di pubblicazione gestita di Google.

L’approvazione della prima release pubblica non richiede di cambiare workflow:
`main` è già configurato per produzione. La prima pubblicazione può comunque
richiedere di completare passaggi in Console che l’API non può sostituire.

## Riprova o promozione di una versione esistente

Usa **Actions → Publish existing release to Google Play → Run workflow**:

- `tag`: un tag GitHub già pubblicato, per esempio `v1.5.0`;
- `track`: `production` oppure `beta`;
- `status`: `completed`, oppure `draft` per preparare una bozza.

Il workflow scarica l’AAB firmato dalla release GitHub. Se un bundle con lo stesso
SHA-256 è già presente su Play, riutilizza il suo versionCode: permette di
promuovere beta → produzione senza un secondo upload. Se la stessa versione è
già selezionata sul canale con lo stesso stato, non modifica né reinvia la release.
Una prerelease `-dev.N` viene rifiutata sul canale di produzione.

## Versioni e riproducibilità

Google Play usa una sola sequenza di versionCode per tutti i canali. Prima di
preparare una release, lo script considera i contatori di tutti i tag stabili e
dev, quindi assegna il successivo. I workflow di release sono serializzati per
impedire che due branch scelgano lo stesso contatore.

Il contatore viene scritto e committato in `gradle.properties`: compilare il tag
non dipende dall’ora o dal numero di esecuzione della CI. F-Droid continua a
seguire soltanto i tag stabili.

## Collegamenti nell’app e revisione

Il pulsante con la stellina è temporaneamente commentato nel layout e nei
listener delle Impostazioni. Prima di ripristinarlo, verificare che la scheda
pubblica di `com.pierspad.betterlauncher` sia disponibile e che l’etichetta
spieghi che serve a recensire BetterLauncher su Google Play. L’helper usa già
l’URL di questa app. Il pulsante Condividi rimanda al repository BetterLauncher.

Il vecchio collegamento alla scheda di Olauncher non deve essere ripristinato:
la prova allegata al rifiuto Play mostrava proprio quella destinazione. L’invio
API completato non equivale all’approvazione della revisione Google.

## Riferimenti

- [Accesso all’API](https://developers.google.com/android-publisher/getting_started)
- [Canali e pubblicazione](https://developers.google.com/android-publisher/tracks)
- [Bundle e SHA-256](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles)
- [Conferma e gestione delle revisioni](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit)
- [Permessi Play Console](https://support.google.com/googleplay/android-developer/answer/9844686)
