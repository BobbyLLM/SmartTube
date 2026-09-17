package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.internal.LocalProfileManager;

import java.util.ArrayList;
import java.util.List;

/** Compatibility entry point retained for callers; it now selects local profiles only. */
public class AccountSelectionPresenter extends BasePresenter<Void> {
    private static AccountSelectionPresenter sInstance;

    public AccountSelectionPresenter(Context context) { super(context); }

    public static AccountSelectionPresenter instance(Context context) {
        if (sInstance == null) sInstance = new AccountSelectionPresenter(context);
        sInstance.setContext(context);
        return sInstance;
    }

    public void show() { show(false); }

    public void show(boolean force) {
        LocalProfileManager profiles = LocalProfileManager.instance();
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();
        for (LocalProfileManager.Profile profile : profiles.list()) {
            options.add(UiOptionItem.from(profile.getName(), item -> {
                profiles.select(profile.getId());
                dialog.closeDialog();
            }, profile.getId().equals(profiles.getActiveId())));
        }
        options.add(UiOptionItem.from("Create local profile", item -> {
            dialog.closeDialog();
            SimpleEditDialog.show(getContext(), "Create local profile", "Profile name", null, value -> {
                profiles.create(value);
                return true;
            });
        }, false));
        options.add(UiOptionItem.from("Rename active profile", item -> {
            dialog.closeDialog();
            LocalProfileManager.Profile active = profiles.getActive();
            SimpleEditDialog.show(getContext(), "Rename local profile", "Profile name", active.getName(), value -> {
                profiles.rename(active.getId(), value);
                return true;
            });
        }, false));
        options.add(UiOptionItem.from("Delete active profile", item -> {
            dialog.closeDialog();
            LocalProfileManager.Profile active = profiles.getActive();
            AppDialogUtil.showConfirmationDialog(getContext(), "Delete " + active.getName() + "?",
                    () -> profiles.delete(active.getId()));
        }, false));
        dialog.appendRadioCategory("Local profiles", options);
        dialog.showDialog(getContext().getString(R.string.app_name), this::unhold);
    }

    public void nextAccountOrDialog() { show(true); }

    public void unhold() { sInstance = null; }

    /** Legacy callers cannot select a Google account in the local-only build. */
    public void selectAccount(Account ignored) { show(true); }
}
