package org.wordpress.android.ui.accounts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.wordpress.android.Constants;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.networking.OAuthAuthenticator;
import org.wordpress.android.stores.Dispatcher;
import org.wordpress.android.stores.action.AccountAction;
import org.wordpress.android.stores.generated.AccountActionBuilder;
import org.wordpress.android.stores.generated.AuthenticationActionBuilder;
import org.wordpress.android.stores.generated.SiteActionBuilder;
import org.wordpress.android.stores.store.AccountStore;
import org.wordpress.android.stores.store.AccountStore.AuthenticatePayload;
import org.wordpress.android.stores.store.AccountStore.NewAccountPayload;
import org.wordpress.android.stores.store.AccountStore.NewUserError;
import org.wordpress.android.stores.store.AccountStore.OnAccountChanged;
import org.wordpress.android.stores.store.AccountStore.OnAuthenticationChanged;
import org.wordpress.android.stores.store.AccountStore.OnNewUserCreated;
import org.wordpress.android.stores.store.SiteStore;
import org.wordpress.android.stores.store.SiteStore.NewSitePayload;
import org.wordpress.android.stores.store.SiteStore.OnNewSiteCreated;
import org.wordpress.android.stores.store.SiteStore.OnSiteChanged;
import org.wordpress.android.stores.store.SiteStore.SiteVisibility;
import org.wordpress.android.ui.notifications.utils.SimperiumUtils;
import org.wordpress.android.util.AlertUtils;
import org.wordpress.android.util.AnalyticsUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.EditTextUtils;
import org.wordpress.android.util.LanguageUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.util.ToastUtils.Duration;
import org.wordpress.android.util.UserEmailUtils;
import org.wordpress.android.widgets.WPTextView;
import org.wordpress.emailchecker2.EmailChecker;
import org.wordpress.persistentedittext.PersistentEditTextHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

public class NewUserFragment extends AbstractFragment implements TextWatcher {
    public static final int NEW_USER = 1;
    private EditText mSiteUrlTextField;
    private EditText mEmailTextField;
    private EditText mPasswordTextField;
    private EditText mUsernameTextField;
    private WPTextView mSignupButton;
    private WPTextView mProgressTextSignIn;
    private RelativeLayout mProgressBarSignIn;
    private boolean mEmailAutoCorrected;
    private boolean mAutoCompleteUrl;
    private String mUsername;
    private String mEmail;
    private String mPassword;

    private NewSitePayload mNewSitePayload;
    private NewAccountPayload mNewAccountPayload;

    protected boolean mSitesFetched = false;
    protected boolean mAccountSettingsFetched = false;
    protected boolean mAccountFetched = false;

    protected @Inject SiteStore mSiteStore;
    protected @Inject AccountStore mAccountStore;
    protected @Inject Dispatcher mDispatcher;

    public static NewUserFragment newInstance() {
        return new NewUserFragment();
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        checkIfFieldsFilled();
    }

    private boolean fieldsFilled() {
        return EditTextUtils.getText(mEmailTextField).trim().length() > 0
                && EditTextUtils.getText(mPasswordTextField).trim().length() > 0
                && EditTextUtils.getText(mUsernameTextField).trim().length() > 0
                && EditTextUtils.getText(mSiteUrlTextField).trim().length() > 0;
    }

    protected void startProgress(String message) {
        mProgressBarSignIn.setVisibility(View.VISIBLE);
        mProgressTextSignIn.setVisibility(View.VISIBLE);
        mSignupButton.setVisibility(View.GONE);
        mProgressBarSignIn.setEnabled(false);
        mProgressTextSignIn.setText(message);
        mEmailTextField.setEnabled(false);
        mPasswordTextField.setEnabled(false);
        mUsernameTextField.setEnabled(false);
        mSiteUrlTextField.setEnabled(false);
    }

    protected void updateProgress(String message) {
        mProgressTextSignIn.setText(message);
    }

    protected void endProgress() {
        mProgressBarSignIn.setVisibility(View.GONE);
        mProgressTextSignIn.setVisibility(View.GONE);
        mSignupButton.setVisibility(View.VISIBLE);
        mEmailTextField.setEnabled(true);
        mPasswordTextField.setEnabled(true);
        mUsernameTextField.setEnabled(true);
        mSiteUrlTextField.setEnabled(true);
    }

    protected void clearErrors() {
        mEmailTextField.setError(null);
        mUsernameTextField.setError(null);
        mPasswordTextField.setError(null);
        mSiteUrlTextField.setError(null);
    }

    protected boolean isUserDataValid() {
        // try to create the user
        final String email = EditTextUtils.getText(mEmailTextField).trim();
        final String password = EditTextUtils.getText(mPasswordTextField).trim();
        final String username = EditTextUtils.getText(mUsernameTextField).trim();
        final String siteUrl = EditTextUtils.getText(mSiteUrlTextField).trim();
        boolean retValue = true;

        if (email.equals("")) {
            showEmailError(getString(R.string.required_field));
            retValue = false;
        }

        final Pattern emailRegExPattern = Patterns.EMAIL_ADDRESS;
        Matcher matcher = emailRegExPattern.matcher(email);
        if (!matcher.find() || email.length() > 100) {
            showEmailError(getString(R.string.invalid_email_message));
            retValue = false;
        }

        if (username.equals("")) {
            showUsernameError(getString(R.string.required_field));
            retValue = false;
        }

        if (username.length() < 4) {
            showUsernameError(getString(R.string.invalid_username_too_short));
            retValue = false;
        }

        if (username.length() > 60) {
            showUsernameError(getString(R.string.invalid_username_too_long));
            retValue = false;
        }

        if (username.contains(" ")) {
            showUsernameError(getString(R.string.invalid_username_no_spaces));
            retValue = false;
        }

        if (siteUrl.contains(" ")) {
            showSiteUrlError(getString(R.string.blog_name_no_spaced_allowed));
            retValue = false;
        }

        if (siteUrl.length() < 4) {
            showSiteUrlError(getString(R.string.blog_name_must_be_at_least_four_characters));
            retValue = false;
        }

        if (password.equals("")) {
            showPasswordError(getString(R.string.required_field));
            retValue = false;
        }

        if (password.length() < 4) {
            showPasswordError(getString(R.string.invalid_password_message));
            retValue = false;
        }

        return retValue;
    }

    protected void onDoneAction() {
        validateAndCreateUserAndBlog();
    }

    private final OnClickListener mSignupClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            validateAndCreateUserAndBlog();
        }
    };

    private final TextView.OnEditorActionListener mEditorAction = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            return onDoneEvent(actionId, event);
        }
    };

    private String siteUrlToSiteName(String siteUrl) {
        return siteUrl;
    }

    private void showPasswordError(String message) {
        mPasswordTextField.setError(message);
        mPasswordTextField.requestFocus();
    }

    private void showEmailError(String message) {
        mEmailTextField.setError(message);
        mEmailTextField.requestFocus();
    }

    private void showUsernameError(String message) {
        mUsernameTextField.setError(message);
        mUsernameTextField.requestFocus();
    }

    private void showSiteUrlError(String message) {
        mSiteUrlTextField.setError(message);
        mSiteUrlTextField.requestFocus();
    }

    private void showSiteError(String message) {
        if (!isAdded()) {
            return;
        }
        showSiteUrlError(message);
    }

    private void showUserError(NewUserError newUserError, String message) {
        if (!isAdded()) {
            return;
        }
        switch (newUserError) {
            case USERNAME_ONLY_LOWERCASE_LETTERS_AND_NUMBERS:
            case USERNAME_REQUIRED:
            case USERNAME_NOT_ALLOWED:
            case USERNAME_MUST_BE_AT_LEAST_FOUR_CHARACTERS:
            case USERNAME_CONTAINS_INVALID_CHARACTERS:
            case USERNAME_MUST_INCLUDE_LETTERS:
            case USERNAME_RESERVED_BUT_MAY_BE_AVAILABLE:
            case USERNAME_INVALID:
                showUsernameError(message);
                break;
            case USERNAME_EXISTS: // Returned error message for username_exists is too vague ("Invalid user input")
                showUsernameError(getString(R.string.username_exists));
                break;
            case PASSWORD_INVALID:
                showPasswordError(message);
                break;
            case EMAIL_CANT_BE_USED_TO_SIGNUP:
            case EMAIL_INVALID:
            case EMAIL_NOT_ALLOWED:
            case EMAIL_EXISTS:
            case EMAIL_RESERVED:
                showEmailError(message);
                break;
            default:
                break;
        }
    }


    private void validateAndCreateUserAndBlog() {
        if (mSystemService.getActiveNetworkInfo() == null) {
            AlertUtils.showAlert(getActivity(), R.string.no_network_title, R.string.no_network_message);
            return;
        }
        if (!isUserDataValid()) {
            return;
        }

        // Prevent double tapping of the "done" btn in keyboard for those clients that don't dismiss the keyboard.
        // Samsung S4 for example
        if (View.VISIBLE == mProgressBarSignIn.getVisibility()) {
            return;
        }

        startProgress(getString(R.string.validating_user_data));
        clearErrors();

        mSitesFetched = false;
        mAccountSettingsFetched = false;
        mAccountFetched = false;

        String siteUrl = EditTextUtils.getText(mSiteUrlTextField).trim();
        mEmail = EditTextUtils.getText(mEmailTextField).trim();
        mUsername = EditTextUtils.getText(mUsernameTextField).trim();
        mPassword = EditTextUtils.getText(mPasswordTextField).trim();
        String siteName = siteUrlToSiteName(siteUrl);
        String language = LanguageUtils.getPatchedCurrentDeviceLanguage(getActivity());

        mNewAccountPayload = new NewAccountPayload(mUsername, mPassword, mEmail, true);
        mNewSitePayload = new NewSitePayload(siteName, siteUrl, language, SiteVisibility.PUBLIC, true);

        mDispatcher.dispatch(AccountActionBuilder.newCreateNewAccountAction(mNewAccountPayload));
        updateProgress(getString(R.string.validating_site_data));

        AppLog.i(T.NUX, "User tries to create a new account, username: " + mUsername + ", email: " + mEmail
                + ", site name: " + siteName + ", site URL: " + siteUrl);
    }

    private void finishCurrentActivity() {
        if (!isAdded()) {
            return;
        }
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
        PersistentEditTextHelper persistentEditTextHelper = new PersistentEditTextHelper(getActivity());
        persistentEditTextHelper.clearSavedText(mEmailTextField, null);
        persistentEditTextHelper.clearSavedText(mUsernameTextField, null);
        persistentEditTextHelper.clearSavedText(mSiteUrlTextField, null);
    }

    /**
     * In case an error happened after the user creation steps, we don't want to show the sign up screen.
     * Show the sign in screen with username and password prefilled, plus a toast message to explain what happened.
     *
     * Note: this should be called only if the user has been created.
     */
    private void finishAndShowSignInScreen() {
        if (!isAdded()) {
            return;
        }
        endProgress();
        Intent intent = new Intent();
        intent.putExtra("username", mUsername);
        intent.putExtra("password", mPassword);
        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
        getFragmentManager().popBackStack();
        ToastUtils.showToast(getActivity(), R.string.signup_succeed_signin_failed, Duration.LONG);
    }

    private void autocorrectEmail() {
        if (mEmailAutoCorrected) {
            return;
        }
        final String email = EditTextUtils.getText(mEmailTextField).trim();
        String suggest = EmailChecker.suggestDomainCorrection(email);
        if (suggest.compareTo(email) != 0) {
            mEmailAutoCorrected = true;
            mEmailTextField.setText(suggest);
            mEmailTextField.setSelection(suggest.length());
        }
    }

    private void initInfoButton(View rootView) {
        ImageView infoBUtton = (ImageView) rootView.findViewById(R.id.info_button);
        infoBUtton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent newAccountIntent = new Intent(getActivity(), HelpActivity.class);
                startActivity(newAccountIntent);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        mDispatcher.register(this);
    }

    @Override
    public void onStop() {
        mDispatcher.unregister(this);
        super.onStop();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getActivity().getApplication()).component().inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout containing a title and body text.
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.new_account_user_fragment_screen, container, false);

        WPTextView termsOfServiceTextView = (WPTextView) rootView.findViewById(R.id.l_agree_terms_of_service);
        termsOfServiceTextView.setText(Html.fromHtml(String.format(getString(R.string.agree_terms_of_service), "<u>",
                "</u>")));
        termsOfServiceTextView.setOnClickListener(new OnClickListener() {
                                                      @Override
                                                      public void onClick(View v) {
                                                          Uri uri = Uri.parse(Constants.URL_TOS);
                                                          startActivity(new Intent(Intent.ACTION_VIEW, uri));
                                                      }
                                                  }
        );

        mSignupButton = (WPTextView) rootView.findViewById(R.id.signup_button);
        mSignupButton.setOnClickListener(mSignupClickListener);
        mSignupButton.setEnabled(false);

        mProgressTextSignIn = (WPTextView) rootView.findViewById(R.id.nux_sign_in_progress_text);
        mProgressBarSignIn = (RelativeLayout) rootView.findViewById(R.id.nux_sign_in_progress_bar);

        mEmailTextField = (EditText) rootView.findViewById(R.id.email_address);
        mEmailTextField.setText(UserEmailUtils.getPrimaryEmail(getActivity()));
        mEmailTextField.setSelection(EditTextUtils.getText(mEmailTextField).length());
        mPasswordTextField = (EditText) rootView.findViewById(R.id.password);
        mUsernameTextField = (EditText) rootView.findViewById(R.id.username);
        mSiteUrlTextField = (EditText) rootView.findViewById(R.id.site_url);

        mEmailTextField.addTextChangedListener(this);
        mPasswordTextField.addTextChangedListener(this);
        mUsernameTextField.addTextChangedListener(this);
        mSiteUrlTextField.setOnKeyListener(mSiteUrlKeyListener);
        mSiteUrlTextField.setOnEditorActionListener(mEditorAction);

        mSiteUrlTextField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkIfFieldsFilled();
            }

            @Override
            public void afterTextChanged(Editable s) {
                BlogUtils.convertToLowercase(s);
            }
        });

        mUsernameTextField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // auto fill blog address
                mSiteUrlTextField.setError(null);
                if (mAutoCompleteUrl) {
                    mSiteUrlTextField.setText(EditTextUtils.getText(mUsernameTextField));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                BlogUtils.convertToLowercase(s);
            }
        });
        mUsernameTextField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mAutoCompleteUrl = EditTextUtils.getText(mUsernameTextField)
                            .equals(EditTextUtils.getText(mSiteUrlTextField))
                            || EditTextUtils.isEmpty(mSiteUrlTextField);
                }
            }
        });

        mEmailTextField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    autocorrectEmail();
                }
            }
        });
        initPasswordVisibilityButton(rootView, mPasswordTextField);
        initInfoButton(rootView);
        return rootView;
    }

    private void checkIfFieldsFilled() {
        if (fieldsFilled()) {
            mSignupButton.setEnabled(true);
        } else {
            mSignupButton.setEnabled(false);
        }
    }

    private final OnKeyListener mSiteUrlKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            mAutoCompleteUrl = EditTextUtils.isEmpty(mSiteUrlTextField);
            return false;
        }
    };

    private SmartLockHelper getSmartLockHelper() {
        if (getActivity() != null && getActivity() instanceof SignInActivity) {
            return ((SignInActivity) getActivity()).getSmartLockHelper();
        }
        return null;
    }

    private void fetchSiteAndAccount() {
        // User has been created. From this point, all errors should close this screen and display the
        // sign in screen
        AnalyticsUtils.refreshMetadataNewUser(mUsername, mEmail);
        AnalyticsTracker.track(AnalyticsTracker.Stat.CREATED_ACCOUNT);
        // Save credentials to smart lock
        SmartLockHelper smartLockHelper = getSmartLockHelper();
        if (smartLockHelper != null) {
            smartLockHelper.saveCredentialsInSmartLock(mUsername, mPassword, mUsername, null);
        }
        // Fetch user infos
        mDispatcher.dispatch(AccountActionBuilder.newFetchAction());
        // Fetch sites
        mDispatcher.dispatch(SiteActionBuilder.newFetchSitesAction());
    }

    // OnChanged events

    @Subscribe
    public void onAuthenticationChanged(OnAuthenticationChanged event) {
        AppLog.i(T.NUX, event.toString());
        if (event.isError) {
            endProgress();
            finishAndShowSignInScreen();
            return;
        }
        if (mAccountStore.hasAccessToken()) {
            // Account created and user authenticated, now create the site
            updateProgress(getString(R.string.creating_your_site));
            mDispatcher.dispatch(SiteActionBuilder.newCreateNewSiteAction(mNewSitePayload));

            // On WordPress.com login, configure Simperium
            AppLog.i(T.NOTIFS, "Configuring Simperium");
            SimperiumUtils.configureSimperium(getContext(), mAccountStore.getAccessToken());
            // Setup legacy access token storage
            OAuthAuthenticator.sAccessToken = mAccountStore.getAccessToken();
        }
    }

    @Subscribe
    public void onNewUserCreated(OnNewUserCreated event) {
        AppLog.i(T.NUX, event.toString());
        if (event.isError) {
            endProgress();
            showUserError(event.errorType, event.errorMessage);
            return;
        }
        if (event.dryRun) {
            // User Validated, now try to validate site creation
            mDispatcher.dispatch(SiteActionBuilder.newCreateNewSiteAction(mNewSitePayload));
            updateProgress(getString(R.string.validating_site_data));
            return;
        }
        // User created, now authenticate the newly created user
        AuthenticatePayload payload = new AuthenticatePayload(mNewAccountPayload.username, mNewAccountPayload.password);
        mDispatcher.dispatch(AuthenticationActionBuilder.newAuthenticateAction(payload));
    }

    @Subscribe
    public void onNewSiteCreated(OnNewSiteCreated event) {
        AppLog.i(T.NUX, event.toString());
        if (event.isError) {
            endProgress();
            showSiteError(event.errorMessage);
            return;
        }
        if (event.dryRun) {
            // User and Site validated, dispatch the same actions with dryRun disabled
            updateProgress(getString(R.string.creating_your_account));
            mNewSitePayload.dryRun = false;
            mNewAccountPayload.dryRun = false;
            mDispatcher.dispatch(AccountActionBuilder.newCreateNewAccountAction(mNewAccountPayload));
            return;
        }
        // Site created, time to wrap up
        fetchSiteAndAccount();
    }

    @Subscribe
    public void onAccountChanged(OnAccountChanged event) {
        AppLog.i(T.NUX, event.toString());
        mAccountSettingsFetched |= event.causeOfChange == AccountAction.FETCH_SETTINGS;
        mAccountFetched |= event.causeOfChange == AccountAction.FETCH_ACCOUNT;
        // Finish activity if sites have been fetched
        if (mSitesFetched && mAccountSettingsFetched && mAccountFetched) {
            finishCurrentActivity();
        }
    }

    @Subscribe
    public void onSiteChanged(OnSiteChanged event) {
        AppLog.i(T.NUX, event.toString());
        mSitesFetched = true;
        // Finish activity if account settings have been fetched
        if (mAccountSettingsFetched && mAccountFetched) {
            finishCurrentActivity();
        }
    }
}
