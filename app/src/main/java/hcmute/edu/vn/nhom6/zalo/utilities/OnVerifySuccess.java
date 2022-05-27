package hcmute.edu.vn.nhom6.zalo.utilities;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import hcmute.edu.vn.nhom6.zalo.activities.login.ChangePassword;
import hcmute.edu.vn.nhom6.zalo.activities.login.CreateAccount;
import hcmute.edu.vn.nhom6.zalo.activities.login.VerifyPhoneNumber;
import hcmute.edu.vn.nhom6.zalo.activities.profile.ChangePasswordActivity;

public class OnVerifySuccess {
    public static void signInWithPhoneAuthCredential(PhoneAuthCredential credential, Intent intent, Context context, FirebaseAuth mAuth, AppCompatActivity activity, String OTP, Class redirectCLass) {
        try {
            credential = PhoneAuthProvider.getCredential(intent.getStringExtra("verificationId"), OTP);
        }catch (Exception e){
            e.printStackTrace();
        }
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(activity, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information4Log.e("VerifyPhone", "signInWithCredential:success");
                            String phone = intent.getStringExtra(Constants.KEY_PHONE_NUMBER);
                            goToCreateAccount(activity, phone);
//                            FirebaseUser user = task.getResult().getUser();
                            // Update UI
                        } else {
                            // Sign in failed, display a message and update the UI
                            Log.e("VerifyPhone", "signInWithCredential:failure", task.getException());
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                // The verification code entered was invalid
                                task.getException().printStackTrace();
                            }
                        }
                    }
                });
    }

    public static void goToCreateAccount(Context context, String phone ){
        Intent intent = new Intent(context, CreateAccount.class);
        intent.putExtra(Constants.KEY_PHONE_NUMBER, phone);
        context.startActivity(intent);
    }

    public static void goToChangePassword(Context context, String phone ){
        Intent intent = new Intent(context, ChangePassword.class);
        intent.putExtra(Constants.KEY_PHONE_NUMBER, phone);
        context.startActivity(intent);
    }
}
