package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class CategoryEmptyError implements ErrorFragmentData {
    private final Context mContext;
    private final Throwable mError;

    public CategoryEmptyError(Context context, @Nullable Throwable error) {
        mContext = context;
        mError = error;
    }

    @Override
    public void onAction() {
        // Anonymous YouTube access is the only supported network path.
    }

    @Override
    public String getMessage() {
        String result = mContext.getString(R.string.msg_cant_load_content);
        if (mError != null && !Helpers.containsAny(mError.getMessage(), "fromNullable result is null")) {
            String className = mError.getClass().getSimpleName();
            result = String.format("%s: %s", className, Utils.getStackTraceAsString(mError));
            //result = mError.getMessage();
        }
        return result;
    }

    @Override
    public String getActionText() {
        return null;
    }
}
