package com.microsoft.services.msa;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import org.apache.http.client.HttpClient;
import org.cryse.unifystorage.providers.onedrive.R;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OneDriveAuthorizationRequest implements ObservableOAuthRequest, OAuthRequestObserver {

    /**
     * OAuthDialog is a Dialog that contains a WebView. The WebView loads the passed in Uri, and
     * loads the passed in WebViewClient that allows the WebView to be observed (i.e., when a page
     * loads the WebViewClient will be notified).
     */
    private class OAuthDialog extends Dialog implements DialogInterface.OnCancelListener {

        /**
         * AuthorizationWebViewClient is a static (i.e., does not have access to the instance that
         * created it) class that checks for when the end_uri is loaded in to the WebView and calls
         * the AuthorizationRequest's onEndUri method.
         */
        private class AuthorizationWebViewClient extends WebViewClient {

            public AuthorizationWebViewClient() {
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mProgressBar.setVisibility(View.VISIBLE);
            }

            /**
             * Call back used when a page is being started.
             *
             * This will check to see if the given URL is one of the end_uris/redirect_uris and
             * based on the query parameters the method will either return an error, or proceed with
             * an AccessTokenRequest.
             *
             * @param view {@link WebView} that this is attached to.
             * @param url of the page being started
             */
            @Override
            public void onPageFinished(WebView view, String url) {
                mProgressBar.setVisibility(View.INVISIBLE);
                view.clearCache(true);
                view.clearHistory();
                Uri uri = Uri.parse(url);

                Uri endUri = mOAuthConfig.getDesktopUri();
                boolean isEndUri = UriComparator.INSTANCE.compare(uri, endUri) == 0;
                if (!isEndUri) {
                    return;
                }

                OneDriveAuthorizationRequest.this.onEndUri(uri);
                clearCookies(view.getContext());
                OAuthDialog.this.dismiss();
            }

            public void clearCookies(Context context) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    //Log.d(C.TAG, "Using ClearCookies code for API >=" + String.valueOf(Build.VERSION_CODES.LOLLIPOP_MR1));
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                } else {
                    //Log.d(C.TAG, "Using ClearCookies code for API <" + String.valueOf(Build.VERSION_CODES.LOLLIPOP_MR1));
                    CookieSyncManager cookieSyncMngr=CookieSyncManager.createInstance(context);
                    cookieSyncMngr.startSync();
                    CookieManager cookieManager=CookieManager.getInstance();
                    cookieManager.removeAllCookie();
                    cookieManager.removeSessionCookie();
                    cookieSyncMngr.stopSync();
                    cookieSyncMngr.sync();
                }
            }

            /**
             * Callback when the WebView received an Error.
             *
             * This method will notify the listener about the error and dismiss the WebView dialog.
             *
             * @param view the WebView that received the error
             * @param errorCode the error code corresponding to a WebViewClient.ERROR_* value
             * @param description the String containing the description of the error
             * @param failingUrl the url that encountered an error
             */
            @Override
            public void onReceivedError(WebView view,
                                        int errorCode,
                                        String description,
                                        String failingUrl) {
                OneDriveAuthorizationRequest.this.onError("", description, failingUrl);
                OAuthDialog.this.dismiss();
            }
        }

        /** Uri to load */
        private final Uri requestUri;

        /**
         * Constructs a new OAuthDialog.
         *
         * @param requestUri to load in the WebView
         */
        public OAuthDialog(Uri requestUri) {
            super(OneDriveAuthorizationRequest.this.activity, android.R.style.Theme_Translucent_NoTitleBar);
            this.setOwnerActivity(OneDriveAuthorizationRequest.this.activity);

            if (requestUri == null) throw new AssertionError();
            this.requestUri = requestUri;
        }

        /** Called when the user hits the back button on the dialog. */
        @Override
        public void onCancel(DialogInterface dialog) {
            LiveAuthException exception = new LiveAuthException(ErrorMessages.SIGNIN_CANCEL);
            OneDriveAuthorizationRequest.this.onException(exception);
        }

        ProgressBar mProgressBar;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            this.setOnCancelListener(this);

            RelativeLayout layout = (RelativeLayout) LayoutInflater.from(getContext()).inflate(R.layout.dialog_authenticate, null);

            WebView webView = (WebView) layout.findViewById(R.id.webview_auth);
            mProgressBar = (ProgressBar) layout.findViewById(R.id.progressbar_auth);

            webView.setWebViewClient(new AuthorizationWebViewClient());

            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);

            webView.loadUrl(this.requestUri.toString());

            this.addContentView(layout,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * Compares just the scheme, authority, and path. It does not compare the query parameters or
     * the fragment.
     */
    private enum UriComparator implements Comparator<Uri> {
        INSTANCE;

        @Override
        public int compare(Uri lhs, Uri rhs) {
            String[] lhsParts = { lhs.getScheme(), lhs.getAuthority(), lhs.getPath() };
            String[] rhsParts = { rhs.getScheme(), rhs.getAuthority(), rhs.getPath() };

            if (lhsParts.length != rhsParts.length) throw new AssertionError();
            for (int i = 0; i < lhsParts.length; i++) {
                int compare = lhsParts[i].compareTo(rhsParts[i]);
                if (compare != 0) {
                    return compare;
                }
            }

            return 0;
        }
    }

    private static final String AMPERSAND = "&";
    private static final String EQUALS = "=";

    /**
     * Turns the fragment parameters of the uri into a map.
     *
     * @param uri to get fragment parameters from
     * @return a map containing the fragment parameters
     */
    private static Map<String, String> getFragmentParametersMap(Uri uri) {
        String fragment = uri.getFragment();
        String[] keyValuePairs = TextUtils.split(fragment, AMPERSAND);
        Map<String, String> fragementParameters = new HashMap<String, String>();

        for (String keyValuePair : keyValuePairs) {
            int index = keyValuePair.indexOf(EQUALS);
            String key = keyValuePair.substring(0, index);
            String value = keyValuePair.substring(index + 1);
            fragementParameters.put(key, value);
        }

        return fragementParameters;
    }

    private final Activity activity;
    private final HttpClient client;
    private final String clientId;
    private final DefaultObservableOAuthRequest observable;
    private final String scope;
    private final String loginHint;
    private final OAuthConfig mOAuthConfig;

    public OneDriveAuthorizationRequest(Activity activity,
                                HttpClient client,
                                String clientId,
                                String scope,
                                String loginHint,
                                final OAuthConfig oAuthConfig) {
        if (activity == null) throw new AssertionError();
        if (client == null) throw new AssertionError();
        if (TextUtils.isEmpty(clientId)) throw new AssertionError();
        if (TextUtils.isEmpty(scope)) throw new AssertionError();

        this.activity = activity;
        this.client = client;
        this.clientId = clientId;
        this.mOAuthConfig = oAuthConfig;
        this.observable = new DefaultObservableOAuthRequest();
        this.scope = scope;
        this.loginHint = loginHint;
    }

    @Override
    public void addObserver(OAuthRequestObserver observer) {
        this.observable.addObserver(observer);
    }

    /**
     * Launches the login/consent page inside of a Dialog that contains a WebView and then performs
     * a AccessTokenRequest on successful login and consent. This method is async and will call the
     * passed in listener when it is completed.
     */
    public void execute() {
        String displayType = this.getDisplayParameter();
        String responseType = OAuth.ResponseType.CODE.toString().toLowerCase(Locale.US);
        String locale = Locale.getDefault().toString();
        final Uri.Builder requestUriBuilder = mOAuthConfig.getAuthorizeUri()
                .buildUpon()
                .appendQueryParameter(OAuth.CLIENT_ID, this.clientId)
                .appendQueryParameter(OAuth.SCOPE, this.scope)
                .appendQueryParameter(OAuth.DISPLAY, displayType)
                .appendQueryParameter(OAuth.RESPONSE_TYPE, responseType)
                .appendQueryParameter(OAuth.LOCALE, locale)
                .appendQueryParameter(OAuth.REDIRECT_URI, mOAuthConfig.getDesktopUri().toString());

        if (this.loginHint != null) {
            requestUriBuilder.appendQueryParameter(OAuth.LOGIN_HINT, this.loginHint);
            requestUriBuilder.appendQueryParameter(OAuth.USER_NAME, this.loginHint);
        }

        Uri requestUri = requestUriBuilder.build();

        OAuthDialog oAuthDialog = new OAuthDialog(requestUri);
        oAuthDialog.show();
    }

    @Override
    public void onException(LiveAuthException exception) {
        this.observable.notifyObservers(exception);
    }

    @Override
    public void onResponse(OAuthResponse response) {
        this.observable.notifyObservers(response);
    }

    @Override
    public boolean removeObserver(OAuthRequestObserver observer) {
        return this.observable.removeObserver(observer);
    }

    /**
     * Gets the display parameter by looking at the screen size of the activity.
     * @return "android_phone" for phones and "android_tablet" for tablets.
     */
    private String getDisplayParameter() {
        ScreenSize screenSize = ScreenSize.determineScreenSize(this.activity);
        DeviceType deviceType = screenSize.getDeviceType();

        return deviceType.getDisplayParameter().toString().toLowerCase(Locale.US);
    }

    /**
     * Called when the response uri contains an access_token in the fragment.
     *
     * This method reads the response and calls back the LiveOAuthListener on the UI/main thread,
     * and then dismisses the dialog window.
     *
     * See <a href="http://tools.ietf.org/html/draft-ietf-oauth-v2-22#section-1.3.1">Section
     * 1.3.1</a> of the OAuth 2.0 spec.
     *
     * @param fragmentParameters in the uri
     */
    private void onAccessTokenResponse(Map<String, String> fragmentParameters) {
        if (fragmentParameters == null) throw new AssertionError();

        OAuthSuccessfulResponse response;
        try {
            response = OAuthSuccessfulResponse.createFromFragment(fragmentParameters);
        } catch (LiveAuthException e) {
            this.onException(e);
            return;
        }

        this.onResponse(response);
    }

    /**
     * Called when the response uri contains an authorization code.
     *
     * This method launches an async AccessTokenRequest and dismisses the dialog window.
     *
     * See <a href="http://tools.ietf.org/html/draft-ietf-oauth-v2-22#section-4.1.2">Section
     * 4.1.2</a> of the OAuth 2.0 spec for more information.
     *
     * @param code is the authorization code from the uri
     */
    private void onAuthorizationResponse(String code) {
        if (TextUtils.isEmpty(code)) throw new AssertionError();

        // Since we DO have an authorization code, launch an AccessTokenRequest.
        // We do this asynchronously to prevent the HTTP IO from occupying the
        // UI/main thread (which we are on right now).
        AccessTokenRequest request = new AccessTokenRequest(this.client,
                this.clientId,
                code,
                mOAuthConfig);

        TokenRequestAsync requestAsync = new TokenRequestAsync(request);
        // We want to know when this request finishes, because we need to notify our
        // observers.
        requestAsync.addObserver(this);
        requestAsync.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Called when the end uri is loaded.
     *
     * This method will read the uri's query parameters and fragment, and respond with the
     * appropriate action.
     *
     * @param endUri that was loaded
     */
    private void onEndUri(Uri endUri) {
        // If we are on an end uri, the response could either be in
        // the fragment or the query parameters. The response could
        // either be successful or it could contain an error.
        // Check all situations and call the listener's appropriate callback.
        // Callback the listener on the UI/main thread. We could call it right away since
        // we are on the UI/main thread, but it is probably better that we finish up with
        // the WebView code before we callback on the listener.
        boolean hasFragment = endUri.getFragment() != null;
        boolean hasQueryParameters = endUri.getQuery() != null;
        boolean invalidUri = !hasFragment && !hasQueryParameters;

        // check for an invalid uri, and leave early
        if (invalidUri) {
            this.onInvalidUri();
            return;
        }

        if (hasFragment) {
            Map<String, String> fragmentParameters =
                    OneDriveAuthorizationRequest.getFragmentParametersMap(endUri);

            boolean isSuccessfulResponse =
                    fragmentParameters.containsKey(OAuth.ACCESS_TOKEN) &&
                            fragmentParameters.containsKey(OAuth.TOKEN_TYPE);
            if (isSuccessfulResponse) {
                this.onAccessTokenResponse(fragmentParameters);
                return;
            }

            String error = fragmentParameters.get(OAuth.ERROR);
            if (error != null) {
                String errorDescription = fragmentParameters.get(OAuth.ERROR_DESCRIPTION);
                String errorUri = fragmentParameters.get(OAuth.ERROR_URI);
                this.onError(error, errorDescription, errorUri);
                return;
            }
        }

        if (hasQueryParameters) {
            String code = endUri.getQueryParameter(OAuth.CODE);
            if (code != null) {
                this.onAuthorizationResponse(code);
                return;
            }

            String error = endUri.getQueryParameter(OAuth.ERROR);
            if (error != null) {
                String errorDescription = endUri.getQueryParameter(OAuth.ERROR_DESCRIPTION);
                String errorUri = endUri.getQueryParameter(OAuth.ERROR_URI);
                this.onError(error, errorDescription, errorUri);
                return;
            }
        }

        // if the code reaches this point, the uri was invalid
        // because it did not contain either a successful response
        // or an error in either the queryParameter or the fragment
        this.onInvalidUri();
    }

    /**
     * Called when end uri had an error in either the fragment or the query parameter.
     *
     * This method constructs the proper exception, calls the listener's appropriate callback method
     * on the main/UI thread, and then dismisses the dialog window.
     *
     * @param error containing an error code
     * @param errorDescription optional text with additional information
     * @param errorUri optional uri that is associated with the error.
     */
    private void onError(String error, String errorDescription, String errorUri) {
        LiveAuthException exception = new LiveAuthException(error,
                errorDescription,
                errorUri);
        this.onException(exception);
    }

    /**
     * Called when an invalid uri (i.e., a uri that does not contain an error or a successful
     * response).
     *
     * This method constructs an exception, calls the listener's appropriate callback on the main/UI
     * thread, and then dismisses the dialog window.
     */
    private void onInvalidUri() {
        LiveAuthException exception = new LiveAuthException(ErrorMessages.SERVER_ERROR);
        this.onException(exception);
    }
}
