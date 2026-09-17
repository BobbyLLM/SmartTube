package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.service.internal.LocalProfileManager;

import java.util.HashMap;
import java.util.Map;

public class AccountsData implements LocalProfileManager.Listener {
    private static final String ACCOUNTS_DATA = "accounts_data";
    @SuppressLint("StaticFieldLeak")
    private static AccountsData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsSelectAccountOnBootEnabled;
    private boolean mIsPasswordAccepted;
    private final Map<String, PasswordItem> mPasswords = new HashMap<>();

    private static class PasswordItem {
        public String accountName;
        public String password;

        public PasswordItem(String accountName, String password) {
            this.accountName = accountName;
            this.password = password;
        }

        public static PasswordItem fromString(String specs) {
            String[] split = Helpers.splitObj(specs);

            if (split == null || split.length != 2) {
                return new PasswordItem(null, null);
            }

            return new PasswordItem(Helpers.parseStr(split[0]), Helpers.parseStr(split[1]));
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(accountName, password);
        }
    }

    private AccountsData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        LocalProfileManager.instance().addListener(this);
        restoreState();
    }

    public static AccountsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountsData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void selectAccountOnBoot(boolean select) {
        mIsSelectAccountOnBootEnabled = select;
        persistState();
    }

    public boolean isSelectAccountOnBootEnabled() {
        return mIsSelectAccountOnBootEnabled;
    }

    public void setAccountPassword(String password) {
        String profileId = getProfileId();
        mPasswords.put(profileId, new PasswordItem(profileId, password));

        persistState();
    }

    public String getAccountPassword() {
        String profileId = getProfileId();
        if (profileId == null) {
            return null;
        }

        PasswordItem passwordItem = mPasswords.get(profileId);

        return passwordItem != null ? passwordItem.password : null;
    }

    public boolean isPasswordAccepted() {
        return mIsPasswordAccepted || getAccountPassword() == null;
    }

    public void setPasswordAccepted(boolean accepted) {
        mIsPasswordAccepted = accepted;
    }

    private void restoreState() {
        String data = mAppPrefs.getData(ACCOUNTS_DATA);

        String[] split = Helpers.splitData(data);

        mIsSelectAccountOnBootEnabled = Helpers.parseBoolean(split, 0, false);
        // mIsAccountProtectedWithPassword
        // mAccountPassword
        String[] passwords = Helpers.parseArray(split, 3);

        if (passwords != null) {
            for (String passwordSpec : passwords) {
                PasswordItem item = PasswordItem.fromString(passwordSpec);
                mPasswords.put(item.accountName, item);
            }
        }
    }

    private void persistState() {
        mAppPrefs.setData(ACCOUNTS_DATA, Helpers.mergeData(
                mIsSelectAccountOnBootEnabled, null, null, Helpers.mergeArray(mPasswords.values().toArray())
        ));
    }

    private String getProfileId() {
        return LocalProfileManager.instance().getActiveId();
    }

    @Override
    public void onProfileChanged() {
        mIsPasswordAccepted = false;
    }
}
