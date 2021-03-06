package hcmute.edu.vn.nhom6.zalo.activities;

import android.content.Intent;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.RequiresApi;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Stack;

import hcmute.edu.vn.nhom6.zalo.R;
import hcmute.edu.vn.nhom6.zalo.activities.search.SearchableActivity;
import hcmute.edu.vn.nhom6.zalo.databinding.ActivityMainBinding;
import hcmute.edu.vn.nhom6.zalo.utilities.Constants;
import hcmute.edu.vn.nhom6.zalo.utilities.MyUtilities;
import hcmute.edu.vn.nhom6.zalo.utilities.PreferenceManager;

public class MainActivity extends BaseActivity/*with user availability*/ {
    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager; // sharedPreference
    private FirebaseFirestore db; // csdl firebase
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_mess, R.id.navigation_contact, R.id.navigation_profile)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(binding.navView, navController);


        db = FirebaseFirestore.getInstance();

        setListeners();

        getToken(); // c???p nh???t token l??n csdl

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteImageAndAudio(); // th???c hi???n x??a file qu?? h???n trong thi???t b???
        }
    }

    private void setListeners() {
        // ?????t s??? ki???n nh???n v??o khung t??m ki???m ng?????i d??ng
        binding.topAppBar.setOnClickListener( v -> {
            startActivity(new Intent(getApplicationContext(), SearchableActivity.class));
        });
    }

    /** H??m c???p nh???t token */
    private void getToken(){
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken); // l???y FCM token t??? firebase messaging v?? c???p nh???t v??o database
//        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(task -> updateToken(task));
    }

    /** H??m c???p nh???t FCM token cho csdl v?? sharedPreference */
    private void updateToken(String token){
        preferenceManager.putString(Constants.KEY_FCM_TOKEN, token); // sharedPreference

        DocumentReference documentReference =
                db.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnSuccessListener(unused -> {
                    MyUtilities.showToast(getApplicationContext(), "Token updated successfully!");
                })
                .addOnFailureListener(e ->{
                    e.printStackTrace();
                    MyUtilities.showToast(getApplicationContext(), "Failed when update token: "+ e.getMessage());
                });
    }

    /** X??a file qu?? th???i h???n */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void deleteImageAndAudio() {
        int lifeCycleDay = preferenceManager.getInt(Constants.KEY_DELETE_PERIOD); // gi?? tr??? th???i h???n t???i  da c???a file do ng?????i d??ng thi???t l???p
        if (lifeCycleDay == -1)
            return; // n???u ng?????i d??ng kh??ng thi???t l???p th?? kh??ng th???c hi???n x??a file
        startDelete(lifeCycleDay);

    }
    /* H??M N??Y CH??A D??NG ?????N*/
    /** H??m x??a file trong th?? m???c v???i th???i gian t???n t???i qu?? th???i h???n*/
    /* T??m ng??y x c??ch hi???n t???i lifeCycle ng??y
    * Sau ???? x??t c??c file trong th?? m???c
    *  n???u ng??y t???o file m?? tr?????c ng??y x th?? x??a file ???? */
    public ArrayList<String> deleteFilesInDir(File dir, /* s??? ng??y t???n t???i */ int lifeCycle ) {
        if (dir == null)
            return null;

        ArrayList<String> filesName = new ArrayList<>();

        /*T???o m???t stack ????? l??u c??c th?? m???c
        ?????u ti??n l?? th?? m???c g???c,
        n???u trong th?? m???c g???c c?? th?? m???c con th?? ti???p t???c th??m th?? m???c con v??o stack n??y
        v?? l???p l???i */
        Stack<File> dirList = new Stack<File>();
        dirList.clear();
        dirList.push(dir);
        while (!dirList.isEmpty()) {
            File dirCurrent = dirList.pop();

            File[] fileList = dirCurrent.listFiles(); // l???y danh s??ch c??c File trong th?? m???c
            for (File file : fileList) {
                if (file.isDirectory()) // File n??y l?? th?? m???c
                    dirList.push(file); // th??m File n??y v??o stack th?? m???c
                else{
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) { // phi??n b???n >= 24
                        Calendar time = Calendar.getInstance(); // L???y th???i gian hi???n t???i v???i calendar
                        time.add((Calendar.DAY_OF_YEAR), -lifeCycle); // l???y ng?????c l???i lifeCycle ng??y
                        Date lastModified = new Date(file.lastModified()); // l???y th???i gian t???o file
                        if (lastModified.before(time.getTime())){ // file qu?? th???i h???n
                            filesName.add(file.getName());
                            file.delete(); // x??a file
                        }
                    }
                }
            }
        }
        return filesName;
    }


    /** h??m x??a file, c???p nh???t gi?? tr??? isStored c???a tin nh???n l??n Firebase ????? n??i r???ng file ???? b??? t??? ?????ng x??a, s??? kh??ng t???i v??? n???a
     * N???i dung l?? :
     * truy v???n tin nh???n m?? ng?????i d??ng hi???n t???i l?? sender x??t tin nh???n l?? d???ng image ho???c audio th?? x??a file trong m??y (n???u c??) v?? c???p nh???t isStore l??n firebase
     * t????ng t??? v???i tin nh???n m?? ng?????i d??ng hi???n t???i l?? receiver*/
    @RequiresApi(api = Build.VERSION_CODES.N) // ??p d???ng cho phi??n b???n 24 tr??? l??n
    private void startDelete(int lifeCycle){
        Calendar time = Calendar.getInstance(); // L???y th???i gian hi???n t???i v???i calendar
        time.add((Calendar.DAY_OF_YEAR), -lifeCycle); // l???y ng?????c l???i lifeCycle ng??y
        // L???y tin nh???n current user l?? ng?????i g???i
        db.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .get()
                .addOnSuccessListener( querySnapshot -> {
                    if( querySnapshot.getDocuments() != null && querySnapshot.getDocuments().size() > 0){
                        for (DocumentSnapshot i: querySnapshot.getDocuments()) {
                            if(i.getBoolean(Constants.KEY_IS_STORED_SENDER)){ // tin nh???n ch??a t??? ?????ng x??a
                                if(i.getDate(Constants.KEY_TIMESTAMP).before(time.getTime())){ // n???u tin nh???n qu?? h???n th?? x??a
                                    if(i.getString(Constants.KEY_MESSAGE_TYPE).equals(Constants.KEY_PICTURE_MESSAGE)){
                                        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(),
                                                Constants.KEY_IMAGE_PATH + File.separator + i.getString(Constants.KEY_MESSAGE));
                                        if(file.exists()){
                                            file.delete(); // x??a file
                                        }
                                    }else if(i.getString(Constants.KEY_MESSAGE_TYPE).equals(Constants.KEY_AUDIO_MESSAGE)){
                                        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(),
                                                Constants.KEY_AUDIO_PATH + File.separator + i.getString(Constants.KEY_MESSAGE));
                                        if(file.exists()){
                                            file.delete();
                                        }
                                    }
                                    updateIsStore(i.getId(), Constants.KEY_IS_STORED_SENDER);
                                }
                            }
                        }
                    }

                    // L???y tin nh???n current user l?? ng?????i nh???n
                    db.collection(Constants.KEY_COLLECTION_CHAT)
                            .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                            .get()
                            .addOnSuccessListener(querySnapshot2 -> {
                                if (querySnapshot2.getDocuments() != null && querySnapshot2.getDocuments().size() > 0) {
                                    for (DocumentSnapshot i : querySnapshot2.getDocuments()) {
                                        if(i.getBoolean(Constants.KEY_IS_STORED_RECEIVER)){ // tin nh???n ch??a t??? ?????ng x??a
                                            if(i.getDate(Constants.KEY_TIMESTAMP).before(time.getTime())){ // n???u tin nh???n qu?? h???n th?? x??a
                                                if(i.getString(Constants.KEY_MESSAGE_TYPE).equals(Constants.KEY_PICTURE_MESSAGE)){
                                                    File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(),
                                                            Constants.KEY_IMAGE_PATH + File.separator + i.getString(Constants.KEY_MESSAGE));
                                                    if(file.exists()){
                                                        file.delete(); // x??a file
                                                    }
                                                }else if(i.getString(Constants.KEY_MESSAGE_TYPE).equals(Constants.KEY_AUDIO_MESSAGE)){
                                                    File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(),
                                                            Constants.KEY_AUDIO_PATH + File.separator + i.getString(Constants.KEY_MESSAGE));
                                                    if(file.exists()){
                                                        file.delete();
                                                    }
                                                }
                                                updateIsStore(i.getId(), Constants.KEY_IS_STORED_RECEIVER);
                                            }
                                        }
                                    }
                                }
                                /* if (list.size() > 0){

                                    for (ChatMessage message: list ) {
                                        if (message.getType().equals(Constants.KEY_PICTURE_MESSAGE)){
                                            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), Constants.KEY_IMAGE_PATH + File.separator + message.getMessage());
                                            if(file.exists()){
                                                file.delete();
                                            }
                                        } else {
                                            if (message.getType().equals(Constants.KEY_PICTURE_MESSAGE)) {
                                                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), Constants.KEY_AUDIO_PATH + File.separator + message.getMessage());
                                                if (file.exists()) {
                                                    file.delete();
                                                }
                                            }
                                        }
                                    }

                                }*/
                            })
                            .addOnFailureListener(e -> {

                            });
                }).addOnFailureListener(e -> {

                });
    }

    /** H??m c???p nh???t gi?? tr??? isStore */
    private void updateIsStore(String messageId, String userField){
        db.collection(Constants.KEY_COLLECTION_CHAT).document(messageId).update(
                userField, false
        );
    }
}