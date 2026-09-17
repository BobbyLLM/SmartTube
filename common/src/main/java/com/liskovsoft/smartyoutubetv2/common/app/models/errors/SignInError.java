package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;

public class SignInError implements ErrorFragmentData {
    private final Context mContext;

    public SignInError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        // Google authentication is not part of SmartTube Local.
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.library_signin_to_show_more);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.dialog_account_none);
    }
}
