package cn.sense.icount.github.base;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import cn.readsense.permissions.PermissionListener;
import cn.readsense.permissions.PermissionsUtil;
import cn.sense.icount.github.util.DisplayUtil;
import cn.sense.icount.github.util.ToastUtils;


public abstract class BaseCoreActivity extends AppCompatActivity {
    ProgressDialog progressDialog;
    Unbinder unbinder;
    public Context context;


    int screenWidth, screenHeight;
    private String permissions[];

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);

        unbinder = ButterKnife.bind(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        progressDialog = new ProgressDialog(context);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);

        screenWidth = DisplayUtil.getScreenWidth(context);
        screenHeight = DisplayUtil.getScreenHeight(context);
        final int layoutId = getLayoutId();
        if (layoutId != 0) {
            setContentView(layoutId);
            if (permissions != null) {
                if (!PermissionsUtil.hasPermission(context, permissions)) {
                    PermissionsUtil.requestPermission(context, new PermissionListener() {
                        @Override
                        public void permissionGranted(@NonNull String[] permission) {
                            initView();
                        }

                        @Override
                        public void permissionDenied(@NonNull String[] permission) {
                            ToastUtils.show("相关请同意权限！！");
                        }
                    }, permissions);
                } else {
                    initView();
                }
            } else {
                initView();
            }
        }

    }

    public void requestPermissions(String[] permissions) {
        this.permissions = permissions;
    }

    public void showToast(String msg) {
        ToastUtils.show(msg);
    }

    public void showProgress(String msg) {
        progressDialog.setMessage(msg);
        progressDialog.show();
    }

    public void dismissProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
    }

    protected abstract int getLayoutId();

    protected abstract void initView();
}
