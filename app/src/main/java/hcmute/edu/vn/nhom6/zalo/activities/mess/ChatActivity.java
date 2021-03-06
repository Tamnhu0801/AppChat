package hcmute.edu.vn.nhom6.zalo.activities.mess;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import com.devlomi.record_view.OnRecordListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

import hcmute.edu.vn.nhom6.zalo.R;
import hcmute.edu.vn.nhom6.zalo.activities.BaseActivity;
import hcmute.edu.vn.nhom6.zalo.activities.profile.FriendProfileActivity;
import hcmute.edu.vn.nhom6.zalo.adapters.ChatAdapter;
import hcmute.edu.vn.nhom6.zalo.databinding.DialogChooseSendPhotoMethodBinding;
import hcmute.edu.vn.nhom6.zalo.databinding.FragmentOpenChatBinding;
import hcmute.edu.vn.nhom6.zalo.models.ChatMessage;
import hcmute.edu.vn.nhom6.zalo.models.User;
import hcmute.edu.vn.nhom6.zalo.network.APIClient;
import hcmute.edu.vn.nhom6.zalo.network.APIService;
import hcmute.edu.vn.nhom6.zalo.utilities.Constants;
import hcmute.edu.vn.nhom6.zalo.utilities.MyActivityForResult;
import hcmute.edu.vn.nhom6.zalo.utilities.MyUtilities;
import hcmute.edu.vn.nhom6.zalo.utilities.PreferenceManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity /*with user availability*/ {

    private static final int REQUEST_CODE_AUDIO_RECORD = 1; // code y??u c???u c???p quy???n record audio
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 2; // code y??u c???u c???p quy???n ghi ??? b??? nh??? ngo??i c???a thi???t b???
    private MediaRecorder mRecorder; // d??ng ????? ghi ??m
    private boolean recordAllow = false; // cho bi???t ???????c c???p ph??p ghi ??m ch??a
    private FragmentOpenChatBinding binding;
    private PreferenceManager preferenceManager; // sharePreference
    private User uReceiver; // ng?????i nh???n tin nh???n
    private ChatAdapter chatAdapter; // adapter ????? hi???n th??? l???ch s??? tin nh???n
    private FirebaseFirestore db; // c?? s??? d??? li???u firebase
    private FirebaseStorage storage; // storage c???a firebase ????? ch???a file
    private ArrayList<ChatMessage> chatList; // danh s??ch tin nh???n
    private String conversionId = null; // id c???a cu???c h???i tho???i
    private String messageType = Constants.KEY_TEXT_MESSAGE; // lo???i tin nh???n ( text, ???nh, audio )
    private boolean isReceiverAvailable = false; // tr???ng th??i ho???t ?????ng c???a ng?????i nh???n
    private String audioPath; // ???????ng d???n audio
    private String audioName; // t??n audio
    private String imagePath; // ???????ng d???n h??nh ???nh
    private String imageName; // t??n h??nh ???nh

    protected final MyActivityForResult<Intent, ActivityResult> activityLauncher = MyActivityForResult.registerActivityForResult(this);
    // thay th??? cho startActivityForResult
    private ActivityResultLauncher<Intent> mActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == RESULT_OK){
                        if(result.getData() != null){
                            Bitmap bitmap = null;
                            if(result.getData().getData() != null){ // N???u ch???n h??nh ???nh t??? th?? vi???n
                                Uri imageUri = result.getData().getData();
                                try{
                                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                                    bitmap = BitmapFactory.decodeStream(inputStream);
                                }catch (FileNotFoundException e){
                                    e.printStackTrace();
                                }
                            }else{ // n???u ch???p ???nh
                                bitmap = (Bitmap) result.getData().getExtras().get("data");
                            }
                            binding.layoutImgPreview.setVisibility(View.VISIBLE);
                            binding.imgPreview.setImageBitmap(bitmap);
                            messageType = Constants.KEY_PICTURE_MESSAGE;
                            binding.edittextChatMessage.setText(null);
                            binding.edittextChatMessage.setEnabled(false);
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentOpenChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetail();
        init();
        listenMessage();
    }

    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatList = new ArrayList<>();
        storage = FirebaseStorage.getInstance();
        chatAdapter = new ChatAdapter(
                chatList,
                MyUtilities.decodeImg(uReceiver.getImage()),
                preferenceManager.getString(Constants.KEY_USER_ID),
                storage.getReference()
        );
        binding.recyclerOpenChannelChat.setAdapter(chatAdapter);
        db = FirebaseFirestore.getInstance();

    }

    /** t???i d??? li???u c???a ng?????i nh???n */
    private void loadReceiverDetail(){
        uReceiver = (User) getIntent().getSerializableExtra(Constants.KEY_USER); // l???y ?????i t?????ng ng?????i nh???n ???????c truy???n l??c nh???n v??o trong danh b??? ho???c m??n h??nh tin nh???n t???ng
        binding.txtName.setText(uReceiver.getName());
    }

    private void setListeners(){
        binding.backButton.setOnClickListener( v -> onBackPressed()); // s??? ki???n nh???n n??t tr??? v??? tr??n android
        binding.buttonOpenChannelChatSend.setOnClickListener(v -> sendMessage());

        binding.buttonOpenChannelChatUpload.setOnClickListener( v -> openDialogSendPhoto());
        binding.buttonDeleteImg.setOnClickListener(v -> {
            binding.imgPreview.setImageBitmap(null);
            binding.layoutImgPreview.setVisibility(View.GONE);
            binding.edittextChatMessage.setEnabled(true); // cho ph??p g?? ch???
            messageType = Constants.KEY_TEXT_MESSAGE; // ki???u text
        });

        binding.imgVoiceMessage.setOnClickListener(v -> {
            onVoiceMessageClick();
        });

        binding.recordView.setOnRecordListener(new OnRecordListener() {
            @Override
            public void onStart() {
                if(startRecordAudio())
                    Log.e("onStart", "B???t ?????u ghi ??m");
                else
                    onCancel();
            }


            @Override
            public void onCancel() {
                Log.e("onCancel", "???? h???y");
                stopRecord();

                //X??a file ghi ??m
                File file = new File(audioPath);
                if(file.exists())
                    file.delete();

                binding.layoutChat.setVisibility(View.VISIBLE);
                binding.layoutRecord.setVisibility(View.GONE);
            }

            @Override
            public void onFinish(long recordTime, boolean limitReached) {
                Log.e("onFinish", "Ghi ??m ho??n t???t");
                stopRecord();
            }

            @Override
            public void onLessThanSecond() {
                MyUtilities.showToast(getApplicationContext(), "??o???n ghi ??m qu?? ng???n!");
                mRecorder.reset();
                mRecorder.release();

                //X??a file ghi ??m
                File file = new File(audioPath);
                if(file.exists())
                    file.delete();
            }
        });

        binding.menuChat.setOnClickListener(v ->{
            showMenu(v);
        });

        binding.txtName.setOnClickListener( v -> {
            openProfile(uReceiver);
        });

    }

    /** M??? trang profile khi nh???n v??o t??n */
    private void openProfile(User user) {
        Intent intent = new Intent(getApplicationContext(), FriendProfileActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
    }

    private void onVoiceMessageClick(){
        if(binding.layoutRecord.getVisibility() != View.VISIBLE){
            setUpRecordAudio();
            if(recordAllow){
                binding.layoutRecord.setVisibility(View.VISIBLE);
                binding.recordButton.setRecordView(binding.recordView);
                binding.edittextChatMessage.setEnabled(false);
                binding.buttonOpenChannelChatUpload.setEnabled(false);
            }
        }else{
            binding.layoutRecord.setVisibility(View.GONE);
            binding.edittextChatMessage.setText(null); // l??m m???i edittext nh???p tin nh???n
            binding.edittextChatMessage.setEnabled(true); // cho ph??p g?? ch???
            binding.buttonOpenChannelChatUpload.setEnabled(true);
            messageType = Constants.KEY_TEXT_MESSAGE; // ki???u text
        }

    }

    /** chu???n b??? c??c quy???n nh?? AudioRecord, ExternalStorage ????? c?? th??? ghi ??m*/
    private void setUpRecordAudio(){
        // Ki???m tra xem ???ng d???ng c?? quy???n truy c???p v??o audio record kh??ng
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED){ // ch??a c?? quy???n
            Log.e("setUpRecordAudio", "no audio record permission!");
            requestAudioRecordPermission(); // y??u c???u quy???n
            return;
        }
        /* n???u SKD version nh??? h??n 30 th?? c???n write external storage permission
        * nh??ng t??? 30 tr??? l??n th?? c???n manage external storage permission*/
        if(Build.VERSION.SDK_INT < 30){
            // Ki???m tra xem ???ng d???ng c?? quy???n truy c???p v??o write_external_storage khong
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) { // ch??a c?? quy???n
                Log.e("setUpRecordAudio", "no write external storage permission!");
                requestWriteExternalStorage(); // y??u c???u quy???n
                return;
            }
        }else{
            // Ki???m tra xem ???ng d???ng c?? quy???n truy c???p v??o manage_external_storage khong
            if (!Environment.isExternalStorageManager()) { // ch??a c?? quy???n
                Log.e("setUpRecordAudio", "no manage external storage permission!");
                requestManageExternalStorage();// y??u c???u quy???n
                return;
            }
        }

        /* xu???ng ?????n ????y c?? ngh??a l?? c??c quy???n truy c???p ???? th???a m??n v???y c?? th??? record ???????c r???i*/
        recordAllow = true;
//        writeExStorageResp = true;
//        audioRecordResp = true;

        binding.layoutRecord.setVisibility(View.VISIBLE);
        binding.recordButton.setRecordView(binding.recordView);
    }
    /** y??u c???u quy???n truy c???p audio record */
    private void requestAudioRecordPermission() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)){ //
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_CODE_AUDIO_RECORD);
        }else{ //
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_CODE_AUDIO_RECORD);
        }
    }
    /** y??u c???u quy???n truy c???p write external storage */
    private void requestWriteExternalStorage() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){ //
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CODE_WRITE_EXTERNAL_STORAGE);
        }else{ //
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CODE_WRITE_EXTERNAL_STORAGE);
        }
    }
    /** y??u c???u quy???n truy c???p manage external storage (cho API 30 tr??? l??n)*/
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void requestManageExternalStorage() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        activityLauncher.launch(intent, result ->{
            if(Environment.isExternalStorageManager()){
                setUpRecordAudio();
            }else{
                MyUtilities.showToast(this, "permission not granted");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CODE_AUDIO_RECORD){
//            audioRecordResp = true;
            if(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                MyUtilities.showToast(this, "permission granted");
                setUpRecordAudio();
            }else{
                MyUtilities.showToast(this, "permission not granted");
            }
        }
        if(requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE){
//            writeExStorageResp = true;
            if(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                MyUtilities.showToast(this, "permission granted");
                setUpRecordAudio();
            }else{
                MyUtilities.showToast(this, "permission not granted");
            }
        }
    }

    /** b???t ?????u ghi ??m*/
    private boolean startRecordAudio() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
//        mRecorder.setOutputFile(getExternalCacheDir()+ "/"+ new Date().toString() + ".3gp");
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), Constants.KEY_AUDIO_PATH);
        if(!file.exists()){
            file.mkdirs();
        }
        audioName = MyUtilities.randomString() + ".3gp";
        audioPath = file.getAbsolutePath() + File.separator + audioName;
//        audioPath = file.getAbsolutePath()+File.separator+ new Date().toString() + ".3gp";
        mRecorder.setOutputFile(audioPath);
        Log.e("AUDIO FILE", audioPath);
        try {
            mRecorder.prepare();
            mRecorder.start();
            messageType = Constants.KEY_AUDIO_MESSAGE;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("startRecord", "C?? l???i khi chu???n b??? ghi ??m");
            return false;
        }
        binding.layoutRecord.setVisibility(View.VISIBLE);
        return true;
    }

    /** k???t th??c ghi ??m v?? gi???i ph??ng t??i nguy??n */
    private boolean stopRecord(){
        try{
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
            return true;
        }catch (IllegalStateException e){
            Log.e("stopRecord", "Audio kh??ng t???n t???i");
        }catch (NullPointerException e){
            Log.e("stopRecord", "Audio kh??ng t???n t???i");
        }
        return false;
    }

    private void openChooseImage(){
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        mActivityResultLauncher.launch(intent);
    }

    /** th???c hi???n l???y l???ch s??? tr?? chuy???n
     * l???ng nghe s??? thay ?????i c???a csdl firebase ??? b???ng chat
     * v???i c??c d??ng chat c?? s??? tham gia c???a current user (sender ho???c receiver)
     * v?? receiver c???a chatActivity (sender ho???c receiver)
     * n???u c?? thay ?????i th?? c???p nh???t l???i recycleView hi???n th??? l???ch s??? tr?? chuy???n c???a 2 user n??y*/
    private void listenMessage(){
        // L???y tin nh???n current user l?? ng?????i g???i
        db.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, uReceiver.getId())
                .addSnapshotListener(eventListener);/*n???u c?? thay ?????i th?? g???i eventListener ????? c???p nh???t recycleView.
                                                                    truy???n v??o activity This ????? listener n??y ch??? l???ng khi ng?????i d??ng ??ang m??? chatActivity*/

        // L???y tin nh???n current user l?? ng?????i nh???n
        db.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, uReceiver.getId())
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    /** th???c hi???n g???i th??ng b??o tin nh???n m???i ?????n ng?????i nh???n khi ng?????i nh???n kh??ng ho???t ?????ng */
    private void sendNotification(String msg){
        // th???c hi???n g???i th??ng b??o
        APIClient.getClient().create(APIService.class).sendMessage(
                Constants.getRemoteMsgHeaders(), // header
                msg // body
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);
                                MyUtilities.showToast(getApplicationContext(), error.getString("error"));
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    MyUtilities.showToast(getApplicationContext(), Constants.MESSAGE_NOTIFICATION_SUCCESS);
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                MyUtilities.showToast(getApplicationContext(), t.getMessage());
            }
        });
    }

    /** x??? l?? tr???ng th??i ho???t ?????ng c???a ng?????i nh???n */
    private void listenAvailabilityOfReceiver(){
        // l???ng nghe s??? thay ?????i c???a ng?????i nh???n
        db.collection(Constants.KEY_COLLECTION_USERS).document(uReceiver.getId())
                .addSnapshotListener(ChatActivity.this, (value, error) ->{
                    if(error != null){
                        error.printStackTrace();
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    if (value != null) {
                        /* l???y tr???ng th??i ho???t ?????ng c???a ng?????i nh???n */
                        if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                            int availability = Objects.requireNonNull(
                                    value.getLong(Constants.KEY_AVAILABILITY)
                            ).intValue();
                            isReceiverAvailable = availability == 1;
                        }
                        uReceiver.setToken(value.getString(Constants.KEY_FCM_TOKEN)); // l???y token c???a ng?????i nh???n
                        if(uReceiver.getImage() == null){
                            // c???p nh???t ???nh ?????i di???n c???a ng?????i nh???n
                            uReceiver.setEncodedImg(value.getString(Constants.KEY_IMAGE));
                            chatAdapter.setReceiverProfileImg(MyUtilities.decodeImg(uReceiver.getImage()));
                            chatAdapter.notifyItemRangeChanged(0, chatList.size());
                        }
                    }
                    // hi???n tr???ng th??i ho???t ?????ng c???a ng?????i nh???n
                    if(isReceiverAvailable){
                        binding.txtStatus.setText(Constants.MESSAGE_TEXT_AVAILABLE);
                        binding.imgStatus.setVisibility(View.VISIBLE);
                    }else {
                        binding.txtStatus.setText(Constants.MESSAGE_TEXT_NOT_AVAILABLE);
                        binding.imgStatus.setVisibility(View.GONE);
                    }
                });
    }

    /** Th???c hi???n c???p nh???t l???ch s??? cu???c tr?? chuy???n hi???n t???i (chatList), (l???y l???ch s??? tr?? chuy???n n???u m???i m??? c???a s???)
     * Ngo??i ra, c??n ki???m tra xem 2 ng?????i d??ng n??y tr?????c ???? ???? c?? tr?? chuy???n ch??a (c?? chung conversation ch??a)
     * N???u ch??a c?? conversation chung th?? t???o conversation cho 2 ng?????i trong firebase*/
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if(error != null){
            return;
        }
        if(value != null){
            int count = chatList.size();
            /* N???u c?? tin nh???n m???i th?? th??m tin nh???n v??o chatList ????? c???p nh???t RecycleView */
            for(DocumentChange i: value.getDocumentChanges()){
                if(i.getType() == DocumentChange.Type.ADDED){ // N???u c?? tin nh???n m???i
                    ChatMessage message = new ChatMessage(
                            i.getDocument().getString(Constants.KEY_SENDER_ID),
                            i.getDocument().getString(Constants.KEY_RECEIVER_ID),
                            i.getDocument().getString(Constants.KEY_MESSAGE),
                            MyUtilities.getStringDate(i.getDocument().getDate(Constants.KEY_TIMESTAMP)),
                            i.getDocument().getString(Constants.KEY_MESSAGE_TYPE),
                            i.getDocument().getDate(Constants.KEY_TIMESTAMP),
                            i.getDocument().getBoolean(Constants.KEY_IS_STORED_SENDER) != null ?
                                    i.getDocument().getBoolean(Constants.KEY_IS_STORED_SENDER):
                                    true,
                            i.getDocument().getBoolean(Constants.KEY_IS_STORED_RECEIVER) != null ?
                                    i.getDocument().getBoolean(Constants.KEY_IS_STORED_RECEIVER):
                                    true
                    );
                    chatList.add(message);
                }
            }
            // s???p x???p danh s??ch tin nh???n theo th???i gian ???????c g???i
            Collections.sort(chatList, (obj1, obj2) -> obj1.getDateObject().compareTo(obj2.getDateObject()));

            /*????? ti???t ki???m t??i nguy??n th?? n???u t???p data c???a adapter ch??a c?? g?? th?? ta m???i g???i notifyDatasetChanged
            * ng?????c l???i th?? g???i notifyItemRangeInserted ????? ch??? th??m tin nh???n m???i v??o ch??? kh??ng c???n ki???m tra l???i to??n b??? tin nh???n trong t???p data c???a adapter*/

            if(count == 0) // n???u l??c ?????u ch??a c?? tin nh???n th?? g???i notifyDataSetChanged ????? c???p nh???t tin nh???n
                chatAdapter.notifyDataSetChanged();
            else { // n???u c?? tin nh???n r???i th?? g???i notifyItemRangeInserted ????? th??m tin nh???n m???i v??o (kh??ng c???n c???p nh???t h???t to??n b???)
                chatAdapter.notifyItemRangeInserted(chatList.size(), chatList.size()); // v??? tr??, s??? l?????ng
                binding.recyclerOpenChannelChat.smoothScrollToPosition(chatList.size() - 1); // tr?????t ?????n v??? tr?? tin nh???n m???i
            }
        }
        if(conversionId == null){
            checkForConversion(); //l???y conversionId
        }
    };

    //T???o conversion ??? db
    private void addConversion(HashMap<String, Object> conversion){
        db.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId()); //c???p nh???t conversionId

    }
    // c???p nh???t conversion
    private void updateConversion(String message){
        // l???y conversion t??? db v???i id l?? conversionId
        DocumentReference documentReference =
                db.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);

        // c???p nh???t conversion -- tin nh???n cu???i c??ng, th???i gian, ng?????i g???i cu???i c??ng
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_MESSAGE_TYPE, messageType,
                Constants.KEY_TIMESTAMP, new Date(),
                Constants.KEY_LAST_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID)
        );
    }
    /** G???i tin nh???n
     * L??u tin nh???n l??n firebase
     * T???o conversation m???i n???u ch??a c?? conversation ???ng v???i tin nh???n hi???n t???i
     * C???p nh???t conversation n???u ???? c?? conversation
     * G???i th??ng b??o ?????n ng?????i nh???n*/
    private void sendMessage(){
        // th??m tin nh???n v??o c?? s??? d??? li???u
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, uReceiver.getId());
        String mes = "";
        if(messageType.equals(Constants.KEY_TEXT_MESSAGE)){ // n???u l?? tin nh???n v??n b???n th?? n???i dung tin nh???n gi??? nguy??n
            mes = binding.edittextChatMessage.getText().toString();
        }else if(messageType.equals(Constants.KEY_PICTURE_MESSAGE)){ // n???u l?? tin nh???n h??nh ???nh hay audio th?? n???i dung tin nh???n l?? t??n file
            saveImage(((BitmapDrawable)binding.imgPreview.getDrawable()).getBitmap()); // l??u h??nh ???nh v??? m??y v?? t???i l??n firebase storage
            mes = imageName;
        }else{
            mes = audioName;
            sendAudioToFireBase(); // l??u audio l??n firebase storage
        }
        message.put(Constants.KEY_MESSAGE, mes);
        message.put(Constants.KEY_MESSAGE_TYPE, messageType);
        message.put(Constants.KEY_TIMESTAMP, new Date());
        message.put(Constants.KEY_IS_STORED_SENDER, true);
        message.put(Constants.KEY_IS_STORED_RECEIVER, true);
        db.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if(conversionId != null){ // n???u conversion ???? t???n t???i (???????c l???y l??c ?????u ??? listenMessage - eventListener)
                                // th?? c???p nh???t conversion
            updateConversion(message.get(Constants.KEY_MESSAGE).toString());
        }else{ // ng?????c l???i t???o conversion m???i
            /* ng?????i g???i h???i tho???i s??? l?? ng?????i d??ng hi???n t???i */
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_RECEIVER_NAME, uReceiver.getName());
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_IMAGE, uReceiver.getImage());
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_RECEIVER_ID, uReceiver.getId());
            conversion.put(Constants.KEY_LAST_MESSAGE, message.get(Constants.KEY_MESSAGE).toString());
            conversion.put(Constants.KEY_MESSAGE_TYPE, messageType);
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            conversion.put(Constants.KEY_LAST_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            addConversion(conversion);
        }

        ////// send notification
        if(!isReceiverAvailable){ // n???u ng?????i nh???n kh??ng tr???c tuy???n th?? g???i th??ng b??o tin nh???n
            try{
                JSONArray tokens = new JSONArray();
                tokens.put(uReceiver.getToken()); // l???y token c???a ng?????i nh???n

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID)); // l???y m?? c???a ng?????i g???i
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME)); // l???y t??n ng?????i g???i
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN)); // l???y token ng?????i g???i

                /* X??t n???u l?? tin nh???n v??n b???n th?? g???i lu??n n???i dung tin nh???n
                *       n???u l?? tin nh???n h??nh ???nh th?? g???i *h??nh ???nh*
                *       n???u l?? tin nh???n tho???i th?? g???i * tin nh???n tho???i * */
                if(messageType.equals(Constants.KEY_AUDIO_MESSAGE))
                    data.put(Constants.KEY_MESSAGE, "*tin nh???n tho???i*");
                else if(messageType.equals(Constants.KEY_PICTURE_MESSAGE))
                    data.put(Constants.KEY_MESSAGE, "*h??nh ???nh*");
                else
                    data.put(Constants.KEY_MESSAGE, mes);

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString()); // g???i th??ng b??o
            }catch (Exception e){
                MyUtilities.showToast(getApplicationContext(), e.getMessage());
                e.printStackTrace();
            }
        }

        binding.edittextChatMessage.setText(null); // l??m m???i edittext nh???p tin nh???n
        binding.imgPreview.setImageBitmap(null);
        binding.layoutImgPreview.setVisibility(View.GONE);
        binding.edittextChatMessage.setEnabled(true); // cho ph??p g?? ch???
        messageType = Constants.KEY_TEXT_MESSAGE; // ki???u text
        binding.layoutRecord.setVisibility(View.GONE);

    }

    /** g???i ??o???n ghi ??m l??n firebase*/
    private void sendAudioToFireBase() {
        StorageReference storageRef = storage.getReference().child(Constants.KEY_AUDIO_PATH + File.separator + audioName);
        Uri file = Uri.fromFile(new File(audioPath));
        storageRef.putFile(file).addOnFailureListener(e -> {
            MyUtilities.showToast(getApplicationContext(), "C?? l???i khi t???i audio l??n firebase");
            e.printStackTrace();
        });
    }

    /** g???i h??nh ???nh l??n firebase*/
    private void sendImageToFireBase() {
        StorageReference storageRef = storage.getReference().child(Constants.KEY_IMAGE_PATH + File.separator + imageName);
        Uri file = Uri.fromFile(new File(imagePath));
        storageRef.putFile(file).addOnFailureListener(e -> {
            MyUtilities.showToast(getApplicationContext(), "C?? l???i khi t???i ???nh l??n firebase");
            e.printStackTrace();
        });
    }

    /** L??u h??nh ???nh v??o m??y v?? g???i l??n firebase*/
    private void saveImage(Bitmap bitmapImage){
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), Constants.KEY_IMAGE_PATH);

        if(!file.exists()){
            file.mkdirs();
        }

        imageName = MyUtilities.randomString() + ".png"; // t??n h??nh ???nh
        imagePath = file.getAbsolutePath() + File.separator + imageName; // ???????ng d???n h??nh ???nh

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(imagePath);
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos); // n??n bitmap v??o file OutputStream
            sendImageToFireBase(); // g???i h??nh ???nh l??n firebase stored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** H??m c???p nh???t */
    private void checkForConversion(){
        if(chatList.size() > 0){ /* N???u c?? tin nh???n r???i
                                 th???c hi???n l???y conversionId
                                 th???c hi???n 2 l???n v?? ?????o sender vs receiver v?? cu???c h???i tho???i mu???n l???y l?? cu???c h???i tho???i c?? s??? tham gia c???a ng?????i d??ng hi???n t???i v?? ng?????i ??ang nh???n tin v???i ng?????i d??ng hi???n t???i
                                 Khi ???? ng?????i d??ng hi???n t???i c?? th??? l?? ng?????i g???i h???i tho???i (sender) ho???c ng?????i nh???n ho???i tho???i (receiver) t????ng t??? ng?????i ??ang nh???n tin v???i ng?????i d??ng hi???n t???i c??ng v???y
                                 n???u kh??ng ?????o l???i th?? c?? th??? b??? s??t */
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    uReceiver.getId()
            );
            checkForConversionRemotely(
                    uReceiver.getId(),
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }
    /** H??m c???p nh???t conversationId */
    private void checkForConversionRemotely(String senderId, String receiverId){
        //L???y conversionId v???i m?? ng?????i g???i v?? ng?????i nh???n v?? c???p nh???t cho bi???n conversionId
        db.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    /** l???y conversionId */
    private OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if( task.isSuccessful() && task.getResult() != null && task.getResult().size() > 0){
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    /** Th??m h??m ki???m tra tr???ng th??i ho???t ?????ng khi resume*/
    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }

    /** H??m t???o dialog ????? ch???n c??ch g???i h??nh ???nh */
    private void openDialogSendPhoto() {
        DialogChooseSendPhotoMethodBinding binding = DialogChooseSendPhotoMethodBinding.inflate(getLayoutInflater());
        PhotoDialog dialog = new PhotoDialog(binding);

        binding.layoutGallery.setOnClickListener(v -> {
            openChooseImage();
            dialog.dismiss();
        });

        binding.layoutCamera.setOnClickListener(v -> {
            if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){ // Ki???m tra n???u ??i???n tho???i c?? camera th?? th???c hi???n ch???p ???nh
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                mActivityResultLauncher.launch(intent);
                dialog.dismiss();
            }else{
                MyUtilities.showToast(getApplicationContext(), Constants.MESSAGE_NO_CAMERA);
            }
        });

        binding.txtCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show(getSupportFragmentManager(), "dialog");
    }

    /** T???o Dialog ????? hi???n th??? cho ng?????i d??ng ch???n ???nh t??? th?? vi???n hay ch???p ???nh m???i */
    public static class PhotoDialog extends DialogFragment {
        DialogChooseSendPhotoMethodBinding binding;
        public PhotoDialog(DialogChooseSendPhotoMethodBinding binding){
            this.binding = binding;
        }
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(binding.getRoot());

            return builder.create();
        }
    }

    /** H??m hi???n menu danh s??ch h??nh ???nh c???a cu???c tr?? chuy???n
     * Tuy nhi??n ch???c n??ng l???y danh s??ch h??nh ???nh cu???c tr?? chuy???n ch??a ???????c th???c hi???n */
    private void showMenu(View view){
        PopupMenu popupMenu = new PopupMenu(getApplicationContext(), view);//View will be an anchor for PopupMenu
        popupMenu.inflate(R.menu.chat_options);
        Menu menu = popupMenu.getMenu();
        popupMenu.setOnMenuItemClickListener(v ->{
            if(v.getItemId() == menu.getItem(0).getItemId()){
                MyUtilities.showToast(getApplicationContext(), "Hi???n danh s??ch h??nh ???nh trong cu???c tr?? chuy???n");
            }
            return true;
        });
        popupMenu.show();
    }

    @Override
    protected void onPause() {
        /* N???u ??ang ghi ??m m?? tho??t ra th?? x??a file ghi ??m */
        if(mRecorder != null){
            if(stopRecord()) {
                File file = new File(audioPath);
                if (file.exists())
                    file.delete();
            }
        }
        super.onPause();
    }
}
