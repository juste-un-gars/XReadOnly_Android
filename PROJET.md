# PROJET : Twitter/X Read-Only pour Android

## Objectif

Application Android native qui charge Twitter/X dans une WebView, en mode **lecture seule** : l'utilisateur peut naviguer, chercher et consulter du contenu avec son compte, mais toutes les interactions (like, retweet, reply, etc.) sont bloquées.

---

## Fonctionnalités principales

### 1. Navigation Twitter en lecture seule

- Charger `https://x.com` dans une WebView Android
- L'utilisateur se connecte avec son compte Twitter normalement
- La session/cookies sont persistés entre les lancements de l'app
- La recherche Twitter fonctionne normalement
- La navigation dans les profils, threads, tendances fonctionne normalement

### 2. Blocage des interactions

Deux niveaux de blocage complémentaires :

#### Niveau UI — Injection CSS/JS

Masquer via CSS (`display: none` / `visibility: hidden`) les éléments suivants :
- Bouton Like (cœur)
- Bouton Retweet / Quote Tweet
- Bouton Reply (répondre)
- Bouton Bookmark
- Bouton Share (partager) natif Twitter
- Zone de composition de tweet (barre "What's happening?")
- Bouton "Post" / "Tweet"
- Tout formulaire de composition (y compris en réponse)

Le JS injecté doit :
- Observer les mutations DOM (MutationObserver) pour cacher les nouveaux éléments au fur et à mesure du scroll infini
- Intercepter les événements click sur les zones d'interaction en fallback

#### Niveau réseau — Blocage des endpoints API

Via `shouldInterceptRequest()` dans le `WebViewClient`, bloquer les requêtes vers les endpoints Twitter/X connus :
- `CreateTweet`
- `FavoriteTweet` / `UnfavoriteTweet`
- `CreateRetweet` / `DeleteRetweet`
- `CreateBookmark` / `DeleteBookmark`
- `DeleteTweet`
- `CreateDM` (messages privés)

Ce blocage réseau est la couche de sécurité la plus robuste : même si un bouton échappe au masquage CSS, l'action sera bloquée côté réseau.

### 3. Liens externes → Chrome en navigation privée

Tout lien qui pointe **en dehors de** `x.com` / `twitter.com` doit :
- Être intercepté (ne PAS s'ouvrir dans la WebView)
- S'ouvrir automatiquement dans **Chrome en mode incognito**

Implémentation via `shouldOverrideUrlLoading()` :
```java
Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
intent.setClassName("com.android.chrome",
    "com.google.android.apps.chrome.IntentDispatcher");
intent.putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true);
startActivity(intent);
```

**Option de fallback** : si Chrome n'est pas installé, ouvrir dans le navigateur par défaut du système.

**Réglage utilisateur** (optionnel, à prévoir) :
- Ouvrir dans Chrome incognito (défaut)
- Ouvrir dans le navigateur par défaut
- Bloquer complètement les liens externes

---

## Architecture technique

### Stack

- **Langage** : Kotlin
- **Min SDK** : 26 (Android 8.0)
- **Composant principal** : `android.webkit.WebView`
- **Pas de dépendance backend** — tout est côté client

### Structure du projet

```
app/
├── src/main/
│   ├── java/com/example/xreadonly/
│   │   ├── MainActivity.kt              # Activité principale avec WebView
│   │   ├── ReadOnlyWebViewClient.kt     # Interception navigation + liens externes
│   │   ├── ReadOnlyWebChromeClient.kt   # Gestion popups, etc.
│   │   ├── RequestBlocker.kt            # Logique de blocage des endpoints API
│   │   └── InjectionScripts.kt          # CSS et JS à injecter
│   ├── assets/
│   │   ├── inject.css                   # CSS pour masquer les boutons
│   │   └── inject.js                    # JS MutationObserver + blocage clicks
│   └── res/
│       └── ...
```

### Comportement WebView

- JavaScript activé (`settings.javaScriptEnabled = true`)
- DOM Storage activé (`settings.domStorageEnabled = true`)
- Cookies persistés via `CookieManager`
- User-Agent : utiliser le UA Chrome mobile standard pour que Twitter serve la version mobile web complète
- Désactiver les popups et redirections non souhaitées

---

## CSS à injecter — Sélecteurs cibles

> ⚠️ Les sélecteurs Twitter/X utilisent des attributs `data-testid` relativement stables. À vérifier et adapter si nécessaire.

```css
/* Boutons d'interaction sur les tweets */
[data-testid="like"],
[data-testid="unlike"],
[data-testid="retweet"],
[data-testid="unretweet"],
[data-testid="reply"],
[data-testid="bookmark"],
[data-testid="removeBookmark"],
[data-testid="caret"] /* menu ··· sur les tweets */
{
  display: none !important;
}

/* Zone de composition */
[data-testid="tweetTextarea_0"],
[data-testid="toolBar"],
[data-testid="tweetButtonInline"],
[data-testid="tweetButton"]
{
  display: none !important;
}

/* Barre de composition en bas */
[data-testid="bottomBar"] {
  display: none !important;
}
```

---

## JS à injecter

```javascript
// MutationObserver pour appliquer le masquage en continu
const observer = new MutationObserver(() => {
  const selectors = [
    '[data-testid="like"]',
    '[data-testid="unlike"]',
    '[data-testid="retweet"]',
    '[data-testid="unretweet"]',
    '[data-testid="reply"]',
    '[data-testid="bookmark"]',
    '[data-testid="removeBookmark"]',
    '[data-testid="tweetTextarea_0"]',
    '[data-testid="tweetButtonInline"]',
    '[data-testid="tweetButton"]',
    '[data-testid="bottomBar"]',
    '[data-testid="caret"]'
  ];
  selectors.forEach(sel => {
    document.querySelectorAll(sel).forEach(el => {
      el.style.display = 'none';
    });
  });
});

observer.observe(document.body, {
  childList: true,
  subtree: true
});
```

---

## Endpoints API à bloquer

Bloquer les requêtes contenant ces patterns dans l'URL :

```
/graphql/ + operationName contenant :
- CreateTweet
- DeleteTweet
- FavoriteTweet
- UnfavoriteTweet
- CreateRetweet
- DeleteRetweet
- CreateBookmark
- DeleteBookmark
- CreateDM

/api/1.1/ :
- /statuses/update
- /favorites/create
- /favorites/destroy
```

---

## Évolutions futures possibles

- **Desktop (Windows/Linux)** : portage via Electron ou Tauri v2 avec la même logique
- **Réglages in-app** : choix du comportement des liens externes, thème sombre forcé, etc.
- **Mise à jour des sélecteurs** : mécanisme pour mettre à jour les sélecteurs CSS/JS sans republier l'app (fichier de config distant ou stockage local éditable)
- **Multi-comptes** : profils WebView séparés avec cookies isolés
