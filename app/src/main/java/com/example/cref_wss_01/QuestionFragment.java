package com.example.cref_wss_01;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

public class QuestionFragment extends Fragment {

    private static final String ARG_QUESTION = "question";
    private static final String ARG_SURVEY_ID = "survey_id";
    private static final String ARG_POSITION = "position";
    private static final String ARG_SUB_QUESTIONS = "sub_questions";

    private SurveyRepository repository;
    private long surveyId;
    private int position;
    private Question question;
    private List<Question> subQuestions = new ArrayList<>();
    private OnAnswerListener listener;

    // Media state — currentMediaQuestionId tracks which question (main or sub) owns the media op
    private Uri currentMediaUri;
    private String currentMediaFilePath;
    private int currentMediaQuestionId;
    private String requestedMedia;

    // Attachment containers per questionId (main + sub-questions)
    private final Map<Integer, LinearLayout> attachmentsContainerMap = new HashMap<>();
    private LinearLayout attachmentsContainer; // kept as a named field for the main question

    private Button deleteButton;
    private boolean isSelectionMode = false;
    private final List<MediaAttachment> selectedAttachments = new ArrayList<>();

    private EditText remarksEditText;
    private TextView debugLogText;
    private final StringBuilder debugLog = new StringBuilder();

    // ── Activity Result Launchers ─────────────────────────────────────────────

    private final ActivityResultLauncher<String> getContentLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    new Thread(() -> {
                        try {
                            File copiedFile = copyUriToInternalStorage(uri);
                            if (copiedFile != null) {
                                String mimeType = requireContext().getContentResolver().getType(uri);
                                String mediaType = "FILE";
                                if (mimeType != null) {
                                    if (mimeType.startsWith("image")) mediaType = "PHOTO";
                                    else if (mimeType.startsWith("video")) mediaType = "VIDEO";
                                    else if (mimeType.startsWith("audio")) mediaType = "AUDIO";
                                }
                                Uri newUri = FileProvider.getUriForFile(requireContext(),
                                        "com.example.cref_wss_01.provider", copiedFile);
                                saveMediaAttachment(newUri, copiedFile.getAbsolutePath(), mediaType);
                            }
                        } catch (IOException e) {
                            log("ERROR copying attached file: " + e.getMessage());
                        }
                    }).start();
                }
            });

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean cameraGranted = Boolean.TRUE.equals(
                        permissions.getOrDefault(Manifest.permission.CAMERA, false));
                boolean audioGranted = Boolean.TRUE.equals(
                        permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false));
                if ("PHOTO".equals(requestedMedia) && cameraGranted) takePhoto();
                else if ("VIDEO".equals(requestedMedia) && cameraGranted && audioGranted) takeVideo();
                else if ("AUDIO".equals(requestedMedia) && audioGranted) recordAudio();
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), result -> {
                if (result && currentMediaUri != null) {
                    saveMediaAttachment(currentMediaUri, currentMediaFilePath, "PHOTO");
                }
            });

    private final ActivityResultLauncher<Uri> takeVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakeVideo(), result -> {
                log("takeVideoLauncher result: " + result);
                if (currentMediaFilePath != null) {
                    File videoFile = new File(currentMediaFilePath);
                    if (videoFile.exists() && videoFile.length() > 0) {
                        saveMediaAttachment(currentMediaUri, currentMediaFilePath, "VIDEO");
                    } else {
                        log("Video capture failed or cancelled.");
                    }
                }
            });

    private final ActivityResultLauncher<Intent> recordAudioLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri audioUri = result.getData().getData();
                    if (audioUri != null) {
                        new Thread(() -> {
                            try {
                                File copiedFile = copyUriToInternalStorage(audioUri);
                                if (copiedFile != null) {
                                    Uri newUri = FileProvider.getUriForFile(requireContext(),
                                            "com.example.cref_wss_01.provider", copiedFile);
                                    saveMediaAttachment(newUri, copiedFile.getAbsolutePath(), "AUDIO");
                                }
                            } catch (IOException e) {
                                log("ERROR copying recorded audio: " + e.getMessage());
                            }
                        }).start();
                    }
                }
            });

    private Runnable pendingLocationAction;

    private final ActivityResultLauncher<String> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingLocationAction != null) pendingLocationAction.run();
                pendingLocationAction = null;
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnAnswerListener) listener = (OnAnswerListener) context;
    }

    public static QuestionFragment newInstance(Question question, long surveyId, int position,
                                               List<Question> subQuestions) {
        QuestionFragment fragment = new QuestionFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_QUESTION, question);
        args.putLong(ARG_SURVEY_ID, surveyId);
        args.putInt(ARG_POSITION, position);
        args.putParcelableArrayList(ARG_SUB_QUESTIONS, new ArrayList<>(subQuestions));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() != null) repository = new SurveyRepository(getActivity().getApplication());
        if (getArguments() != null) {
            question = getArguments().getParcelable(ARG_QUESTION);
            surveyId = getArguments().getLong(ARG_SURVEY_ID);
            position = getArguments().getInt(ARG_POSITION);
            List<Question> subs = getArguments().getParcelableArrayList(ARG_SUB_QUESTIONS);
            if (subs != null) subQuestions = subs;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_question, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        debugLogText = view.findViewById(R.id.debug_log_text);
        log("onViewCreated");

        attachmentsContainer = view.findViewById(R.id.attachments_container);
        remarksEditText = view.findViewById(R.id.remarks_edit_text);

        if (question != null) {
            currentMediaQuestionId = question.getId();
            attachmentsContainerMap.put(question.getId(), attachmentsContainer);
        }

        // Main question media buttons
        ImageButton addPhotoButton = view.findViewById(R.id.add_photo_button);
        ImageButton addVideoButton = view.findViewById(R.id.add_video_button);
        ImageButton addAudioButton = view.findViewById(R.id.add_audio_button);
        ImageButton attachFileButton = view.findViewById(R.id.attach_file_button);

        addPhotoButton.setOnClickListener(v -> {
            currentMediaQuestionId = question.getId();
            requestedMedia = "PHOTO";
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA});
        });
        addVideoButton.setOnClickListener(v -> {
            currentMediaQuestionId = question.getId();
            requestedMedia = "VIDEO";
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        });
        addAudioButton.setOnClickListener(v -> {
            currentMediaQuestionId = question.getId();
            requestedMedia = "AUDIO";
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
        });
        attachFileButton.setOnClickListener(v -> {
            currentMediaQuestionId = question.getId();
            getContentLauncher.launch("*/*");
        });

        // Navigation buttons
        Button previousButton = view.findViewById(R.id.previous_button);
        Button closeButton = view.findViewById(R.id.close_button);
        Button nextButton = view.findViewById(R.id.next_button);

        previousButton.setEnabled(position > 0);
        previousButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).goToPreviousQuestion();
        });
        closeButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).closeSurvey();
        });
        nextButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).goToNextQuestion();
        });

        if (getContext() != null) {
            deleteButton = new Button(getContext());
            deleteButton.setText("Delete Selected");
            deleteButton.setVisibility(View.GONE);
            deleteButton.setOnClickListener(v -> deleteSelectedAttachments());
            ((LinearLayout) view.findViewById(R.id.question_root)).addView(deleteButton);
        }

        if (question != null) {
            TextView categoryTextView = view.findViewById(R.id.category_text);
            TextView questionTextView = view.findViewById(R.id.question_text);
            categoryTextView.setText(question.getCategory() + " > " + question.getSubCategory());
            questionTextView.setText(question.getQuestionText());

            remarksEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { saveRemarks(s.toString()); }
            });

            addAnswerControls();
            loadAttachments();
        }
    }

    // ── Debug logging ──────────────────────────────────────────────────────────

    private void log(String message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            debugLog.append(message).append("\n");
            if (debugLogText != null) debugLogText.setText(debugLog.toString());
        });
    }

    // ── Media capture ──────────────────────────────────────────────────────────

    private void takePhoto() {
        if (getContext() == null) return;
        try {
            File mediaFile = createMediaFile("PHOTO");
            currentMediaFilePath = mediaFile.getAbsolutePath();
            currentMediaUri = FileProvider.getUriForFile(getContext(),
                    "com.example.cref_wss_01.provider", mediaFile);
            takePictureLauncher.launch(currentMediaUri);
        } catch (IOException ex) {
            log("ERROR creating photo file: " + ex.getMessage());
            Toast.makeText(getContext(), "Error creating image file", Toast.LENGTH_SHORT).show();
        }
    }

    private void takeVideo() {
        if (getContext() == null) return;
        try {
            File mediaFile = createMediaFile("VIDEO");
            currentMediaFilePath = mediaFile.getAbsolutePath();
            currentMediaUri = FileProvider.getUriForFile(getContext(),
                    "com.example.cref_wss_01.provider", mediaFile);
            takeVideoLauncher.launch(currentMediaUri);
        } catch (IOException ex) {
            log("ERROR creating video file: " + ex.getMessage());
            Toast.makeText(getContext(), "Error creating video file", Toast.LENGTH_SHORT).show();
        }
    }

    private void recordAudio() {
        log("Attempting to record audio...");
        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        recordAudioLauncher.launch(intent);
    }

    private File createMediaFile(String type) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileExtension, environmentDir, prefix;
        if ("PHOTO".equals(type)) {
            fileExtension = ".jpg"; environmentDir = Environment.DIRECTORY_PICTURES; prefix = "JPEG_";
        } else {
            fileExtension = ".mp4"; environmentDir = Environment.DIRECTORY_MOVIES; prefix = "MP4_";
        }
        String fileName = prefix + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(environmentDir);
        return File.createTempFile(fileName, fileExtension, storageDir);
    }

    // ── Media attachment saving ────────────────────────────────────────────────

    private void saveMediaAttachment(Uri uri, @Nullable String filePath, String type) {
        final int targetQuestionId = currentMediaQuestionId;
        repository.getAnswer(surveyId, targetQuestionId, existingAnswer -> {
            Runnable saveAttachment = () -> {
                MediaAttachment attachment = new MediaAttachment();
                attachment.surveyId = surveyId;
                attachment.questionId = targetQuestionId;
                attachment.uri = uri.toString();
                attachment.filePath = filePath;
                attachment.mediaType = type;
                repository.saveMediaAttachment(attachment, () -> onMediaSaved(targetQuestionId));
            };
            if (existingAnswer == null) {
                log("No existing answer for Q" + targetQuestionId + ". Creating empty answer first.");
                saveAnswer(targetQuestionId, "", saveAttachment);
            } else {
                saveAttachment.run();
            }
        });
    }

    private void onMediaSaved(int questionId) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            log("Media saved for Q" + questionId + ". Refreshing attachments.");
            LinearLayout container = attachmentsContainerMap.get(questionId);
            if (container != null) loadAttachments(questionId, container);
            if (listener != null) listener.onAnswerSaved();
        });
    }

    // ── Attachment loading & display ──────────────────────────────────────────

    private void loadAttachments() {
        loadAttachments(question.getId(), attachmentsContainer);
    }

    private void loadAttachments(int questionId, LinearLayout container) {
        if (repository == null) return;
        repository.getAttachmentsForQuestion(surveyId, questionId, attachments -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                container.removeAllViews();
                for (MediaAttachment attachment : attachments) {
                    addAttachmentThumbnail(attachment, container);
                }
            });
        });
    }

    private void addAttachmentThumbnail(MediaAttachment attachment, LinearLayout container) {
        if (getContext() == null) return;

        FrameLayout frameLayout = new FrameLayout(getContext());
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(200, 200);
        frameParams.setMargins(0, 0, 16, 0);
        frameLayout.setLayoutParams(frameParams);

        ImageView imageView = new ImageView(getContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        if ("PHOTO".equals(attachment.mediaType)) {
            imageView.setImageURI(Uri.parse(attachment.uri));
            frameLayout.addView(imageView);
        } else if ("VIDEO".equals(attachment.mediaType)) {
            Bitmap thumbnail = null;
            if (attachment.filePath != null) {
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(attachment.filePath);
                    thumbnail = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    retriever.release();
                } catch (Exception e) { e.printStackTrace(); }
            }
            if (thumbnail != null) imageView.setImageBitmap(thumbnail);
            else imageView.setImageResource(R.drawable.ic_videocam);
            frameLayout.addView(imageView);
            ImageView playIcon = new ImageView(getContext());
            FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            playParams.gravity = Gravity.CENTER;
            playIcon.setLayoutParams(playParams);
            playIcon.setImageResource(R.drawable.ic_play_circle);
            frameLayout.addView(playIcon);
        } else {
            imageView.setImageResource(R.drawable.ic_mic);
            frameLayout.addView(imageView);
        }

        frameLayout.setTag(attachment);
        frameLayout.setOnClickListener(this::onAttachmentClick);
        frameLayout.setOnLongClickListener(this::onAttachmentLongClick);
        container.addView(frameLayout);
    }

    private void onAttachmentClick(View view) {
        MediaAttachment attachment = (MediaAttachment) view.getTag();
        if (isSelectionMode) {
            toggleAttachmentSelection(attachment, view);
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(attachment.uri);
            String type;
            if ("PHOTO".equals(attachment.mediaType)) type = "image/*";
            else if ("VIDEO".equals(attachment.mediaType)) type = "video/*";
            else type = "audio/*";
            intent.setDataAndType(uri, type);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }
    }

    private boolean onAttachmentLongClick(View view) {
        if (!isSelectionMode) {
            isSelectionMode = true;
            if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
            toggleAttachmentSelection((MediaAttachment) view.getTag(), view);
        }
        return true;
    }

    private void toggleAttachmentSelection(MediaAttachment attachment, View view) {
        if (selectedAttachments.contains(attachment)) {
            selectedAttachments.remove(attachment);
            view.setAlpha(1.0f);
        } else {
            selectedAttachments.add(attachment);
            view.setAlpha(0.5f);
        }
    }

    private void deleteSelectedAttachments() {
        Map<Integer, Boolean> affectedIds = new HashMap<>();
        for (MediaAttachment attachment : selectedAttachments) {
            repository.deleteMediaAttachment(attachment);
            affectedIds.put(attachment.questionId, true);
        }
        exitSelectionMode();
        for (int qId : affectedIds.keySet()) {
            LinearLayout container = attachmentsContainerMap.get(qId);
            if (container != null) loadAttachments(qId, container);
        }
        if (listener != null) listener.onAnswerSaved();
    }

    public void exitSelectionMode() {
        isSelectionMode = false;
        selectedAttachments.clear();
        if (deleteButton != null) deleteButton.setVisibility(View.GONE);
        for (LinearLayout container : attachmentsContainerMap.values()) {
            for (int i = 0; i < container.getChildCount(); i++) {
                container.getChildAt(i).setAlpha(1.0f);
            }
        }
    }

    // ── Answer controls ────────────────────────────────────────────────────────

    private void addAnswerControls() {
        if (getView() == null) return;
        repository.getAnswer(surveyId, question.getId(), currentAnswer -> {
            repository.getAllPreviousAnswersForQuestion(surveyId, question.getId(), allPreviousAnswers -> {
                if (getActivity() == null || getView() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getContext() == null) return;
                    LinearLayout container = getView().findViewById(R.id.answers_container);
                    if (currentAnswer != null && remarksEditText != null) {
                        remarksEditText.setText(currentAnswer.remarks);
                    }
                    populateAnswerControls(question.getId(), question.getAnswerType(),
                            question.getAnswerOptions(), container, currentAnswer,
                            allPreviousAnswers, subQuestions);
                });
            });
        });
    }

    private void populateAnswerControls(int questionId, String answerType, String answerOptions,
            LinearLayout container, Answer currentAnswer, List<String> allPreviousAnswers,
            List<Question> subs) {
        if (getContext() == null || answerType == null) return;
        switch (answerType) {
            case "MULTIPLE_CHOICE_SINGLE": {
                String[] opts = (answerOptions != null && !answerOptions.isEmpty())
                        ? answerOptions.split("\\|") : new String[0];
                addRadioButtons(questionId, opts, container, currentAnswer, allPreviousAnswers, subs);
                break;
            }
            case "MULTIPLE_CHOICE_MULTI": {
                String[] opts = (answerOptions != null && !answerOptions.isEmpty())
                        ? answerOptions.split("\\|") : new String[0];
                addCheckBoxes(questionId, opts, container, currentAnswer, allPreviousAnswers);
                break;
            }
            case "NUMBER":
                addNumberInput(questionId, container, currentAnswer, allPreviousAnswers, answerOptions);
                break;
            case "TEXT":
                addEditText(questionId, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                        container, currentAnswer, allPreviousAnswers);
                break;
            case "DATE_YEAR":
                addYearPicker(questionId, container, currentAnswer, answerOptions);
                break;
            case "DATE_YEAR_MONTH":
                addYearMonthPicker(questionId, container, currentAnswer, answerOptions);
                break;
            case "DATE_FULLDATE":
                addDatePicker(questionId, container, currentAnswer);
                break;
            case "TIME":
                addTimePicker(questionId, container, currentAnswer);
                break;
            case "DATE_TIME":
                addDateTimePicker(questionId, container, currentAnswer);
                break;
            case "DURATION":
                addDurationInput(questionId, container, currentAnswer, answerOptions);
                break;
            case "LOCATION":
                addLocationInput(questionId, container, currentAnswer);
                break;
        }
    }

    private void addRadioButtons(int questionId, String[] options, LinearLayout container,
            Answer existingAnswer, List<String> allPreviousAnswers, List<Question> subs) {
        if (getContext() == null) return;

        // Group sub-questions by triggerValue (preserving insertion order)
        Map<String, List<Question>> subsByTrigger = new LinkedHashMap<>();
        if (subs != null) {
            for (Question sub : subs) {
                String tv = sub.getTriggerValue() != null ? sub.getTriggerValue() : "";
                subsByTrigger.computeIfAbsent(tv, k -> new ArrayList<>()).add(sub);
            }
        }

        RadioGroup radioGroup = new RadioGroup(getContext());
        radioGroup.setLayoutParams(new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (String option : options) {
            RadioButton rb = new RadioButton(getContext());
            int subCount = subsByTrigger.containsKey(option) ? subsByTrigger.get(option).size() : 0;
            String displayText = option + (subCount > 0
                    ? "  ▸ " + subCount + " follow-up" + (subCount > 1 ? "s" : "") : "");
            rb.setText(displayText);
            rb.setTextSize(16);
            if (existingAnswer != null && option.equals(existingAnswer.answerValue)) {
                rb.setChecked(true);
            }
            radioGroup.addView(rb);
        }

        container.addView(radioGroup);

        // Build and add sub-question cards
        Map<String, LinearLayout> triggerCardMap = new LinkedHashMap<>();
        String currentVal = existingAnswer != null ? existingAnswer.answerValue : null;

        for (Map.Entry<String, List<Question>> entry : subsByTrigger.entrySet()) {
            String triggerVal = entry.getKey();
            LinearLayout card = buildSubQuestionsCard(entry.getValue());
            card.setVisibility(triggerVal.equals(currentVal) ? View.VISIBLE : View.GONE);
            container.addView(card);
            triggerCardMap.put(triggerVal, card);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected == null) return;
            String raw = selected.getText().toString();
            int arrowIdx = raw.indexOf("  ▸");
            String plain = (arrowIdx >= 0 ? raw.substring(0, arrowIdx) : raw)
                    .split("\\[")[0].trim();
            saveAnswer(questionId, plain);

            if (!triggerCardMap.isEmpty()) {
                TransitionManager.beginDelayedTransition(container, new AutoTransition());
                triggerCardMap.forEach((tv, card) ->
                        card.setVisibility(tv.equals(plain) ? View.VISIBLE : View.GONE));
            }
        });
    }

    private void addCheckBoxes(int questionId, String[] options, LinearLayout container,
            Answer existingAnswer, List<String> allPreviousAnswers) {
        if (getContext() == null) return;
        List<String> savedAnswers = new ArrayList<>();
        if (existingAnswer != null && existingAnswer.answerValue != null) {
            savedAnswers.addAll(Arrays.asList(existingAnswer.answerValue.split("\\|")));
        }
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String option : options) {
            CheckBox cb = new CheckBox(getContext());
            cb.setText(option);
            cb.setTextSize(16);
            if (savedAnswers.contains(option)) cb.setChecked(true);
            cb.setOnCheckedChangeListener((btn, isChecked) -> saveCheckBoxAnswers(questionId, checkBoxes));
            checkBoxes.add(cb);
            container.addView(cb);
        }
    }

    private void saveCheckBoxAnswers(int questionId, List<CheckBox> checkBoxes) {
        StringBuilder sb = new StringBuilder();
        for (CheckBox cb : checkBoxes) {
            if (cb.isChecked()) {
                if (sb.length() > 0) sb.append("|");
                sb.append(cb.getText().toString().split("\\[")[0].trim());
            }
        }
        saveAnswer(questionId, sb.toString());
    }

    private void addNumberInput(int questionId, LinearLayout container, Answer existingAnswer,
            List<String> allPreviousAnswers, String options) {
        if (getContext() == null) return;

        String[] tokens = (options != null && !options.isEmpty())
                ? options.split("\\|", -1) : new String[]{"DECIMAL"};
        String format = tokens.length > 0 ? tokens[0].trim().toUpperCase(Locale.US) : "DECIMAL";
        Double min = tokens.length > 1 && !tokens[1].trim().isEmpty()
                ? parseDoubleOrNull(tokens[1].trim()) : null;
        Double max = tokens.length > 2 && !tokens[2].trim().isEmpty()
                ? parseDoubleOrNull(tokens[2].trim()) : null;

        int inputType;
        String hint = "";
        switch (format) {
            case "INTEGER":
                inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
                if (min != null && max != null)
                    hint = "[" + min.intValue() + " – " + max.intValue() + "]";
                break;
            case "PERCENTAGE":
                inputType = InputType.TYPE_CLASS_NUMBER;
                hint = "% (0–100)";
                if (min == null) min = 0.0;
                if (max == null) max = 100.0;
                break;
            default: // DECIMAL
                inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED;
                if (min != null && max != null)
                    hint = "[" + min + " – " + max + "]";
                break;
        }

        final Double finalMin = min;
        final Double finalMax = max;

        List<String> prevAnswers = allPreviousAnswers.stream().distinct().collect(Collectors.toList());
        android.widget.AutoCompleteTextView editText =
                new android.widget.AutoCompleteTextView(getContext());
        editText.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editText.setInputType(inputType);
        editText.setTextSize(16);
        if (!hint.isEmpty()) editText.setHint(hint);
        editText.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, prevAnswers));
        editText.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) editText.showDropDown(); });
        if (existingAnswer != null && existingAnswer.answerValue != null) {
            editText.setText(existingAnswer.answerValue);
        }

        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String val = s.toString().trim();
                saveAnswer(questionId, val);
                if (!val.isEmpty() && (finalMin != null || finalMax != null)) {
                    try {
                        double dv = Double.parseDouble(val);
                        if (finalMin != null && dv < finalMin)
                            editText.setError("Min: " + finalMin.intValue());
                        else if (finalMax != null && dv > finalMax)
                            editText.setError("Max: " + finalMax.intValue());
                        else editText.setError(null);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
        container.addView(editText);
    }

    private void addEditText(int questionId, int inputType, LinearLayout container,
            Answer existingAnswer, List<String> allPreviousAnswers) {
        if (getContext() == null) return;
        List<String> prevAnswers = allPreviousAnswers.stream().distinct().collect(Collectors.toList());
        android.widget.AutoCompleteTextView editText =
                new android.widget.AutoCompleteTextView(getContext());
        editText.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editText.setInputType(inputType);
        editText.setTextSize(16);
        editText.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, prevAnswers));
        editText.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) editText.showDropDown(); });
        if (existingAnswer != null && existingAnswer.answerValue != null) {
            editText.setText(existingAnswer.answerValue);
        }
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveAnswer(questionId, s.toString()); }
        });
        container.addView(editText);
    }

    private void addYearPicker(int questionId, LinearLayout container,
            Answer existingAnswer, String options) {
        if (getContext() == null) return;
        String[] tokens = (options != null && !options.isEmpty()) ? options.split("\\|") : new String[0];
        int minYear = tokens.length > 0 ? parseIntOrDefault(tokens[0], 1950) : 1950;
        int maxYear = tokens.length > 1
                ? parseIntOrDefault(tokens[1], Calendar.getInstance().get(Calendar.YEAR) + 5)
                : Calendar.getInstance().get(Calendar.YEAR) + 5;

        List<String> years = new ArrayList<>();
        for (int y = maxYear; y >= minYear; y--) years.add(String.valueOf(y));

        Spinner spinner = new Spinner(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, years);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (existingAnswer != null && existingAnswer.answerValue != null
                && !existingAnswer.answerValue.isEmpty()) {
            int pos = years.indexOf(existingAnswer.answerValue);
            if (pos >= 0) spinner.setSelection(pos, false);
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean initialized = false;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!initialized) { initialized = true; return; }
                saveAnswer(questionId, years.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        container.addView(spinner);
    }

    private void addYearMonthPicker(int questionId, LinearLayout container,
            Answer existingAnswer, String options) {
        if (getContext() == null) return;
        String[] tokens = (options != null && !options.isEmpty()) ? options.split("\\|") : new String[0];
        int minYear = tokens.length > 0 ? parseIntOrDefault(tokens[0], 1950) : 1950;
        int maxYear = tokens.length > 1
                ? parseIntOrDefault(tokens[1], Calendar.getInstance().get(Calendar.YEAR) + 5)
                : Calendar.getInstance().get(Calendar.YEAR) + 5;

        List<String> years = new ArrayList<>();
        for (int y = maxYear; y >= minYear; y--) years.add(String.valueOf(y));

        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        String[] monthCodes = {"01","02","03","04","05","06","07","08","09","10","11","12"};

        String initYear = years.get(0);
        int initMonthIdx = 0;
        if (existingAnswer != null && existingAnswer.answerValue != null
                && existingAnswer.answerValue.contains("-")) {
            String[] parts = existingAnswer.answerValue.split("-");
            if (years.contains(parts[0])) initYear = parts[0];
            if (parts.length > 1) {
                for (int i = 0; i < monthCodes.length; i++) {
                    if (monthCodes[i].equals(parts[1])) { initMonthIdx = i; break; }
                }
            }
        }

        final String[] selYear = {initYear};
        final int[] selMonthIdx = {initMonthIdx};

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

        Spinner yearSpinner = new Spinner(getContext());
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setSelection(Math.max(0, years.indexOf(initYear)), false);

        Spinner monthSpinner = new Spinner(getContext());
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, Arrays.asList(monthNames));
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);
        monthSpinner.setSelection(initMonthIdx, false);

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMarginEnd(dpToPx(4));
        yearSpinner.setLayoutParams(p1);
        monthSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        yearSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean initialized = false;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!initialized) { initialized = true; return; }
                selYear[0] = years.get(pos);
                saveAnswer(questionId, selYear[0] + "-" + monthCodes[selMonthIdx[0]]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean initialized = false;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!initialized) { initialized = true; return; }
                selMonthIdx[0] = pos;
                saveAnswer(questionId, selYear[0] + "-" + monthCodes[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        row.addView(yearSpinner);
        row.addView(monthSpinner);
        container.addView(row);
    }

    private void addDatePicker(int questionId, LinearLayout container, Answer existingAnswer) {
        if (getContext() == null) return;
        final String[] val = {existingAnswer != null && existingAnswer.answerValue != null
                ? existingAnswer.answerValue : ""};
        Button btn = new Button(getContext());
        btn.setText(val[0].isEmpty() ? "Select date" : val[0]);
        btn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (!val[0].isEmpty()) {
                try {
                    String[] p = val[0].split("-");
                    cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
            }
            new DatePickerDialog(requireContext(), (dp, y, m, d) -> {
                val[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                btn.setText(val[0]);
                saveAnswer(questionId, val[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        container.addView(btn);
    }

    private void addTimePicker(int questionId, LinearLayout container, Answer existingAnswer) {
        if (getContext() == null) return;
        final String[] val = {existingAnswer != null && existingAnswer.answerValue != null
                ? existingAnswer.answerValue : ""};
        Button btn = new Button(getContext());
        btn.setText(val[0].isEmpty() ? "Select time" : val[0]);
        btn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (!val[0].isEmpty()) {
                try {
                    String[] p = val[0].split(":");
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(p[1]));
                } catch (Exception ignored) {}
            }
            new TimePickerDialog(requireContext(), (tp, h, min) -> {
                val[0] = String.format(Locale.US, "%02d:%02d", h, min);
                btn.setText(val[0]);
                saveAnswer(questionId, val[0]);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        });
        container.addView(btn);
    }

    private void addDateTimePicker(int questionId, LinearLayout container, Answer existingAnswer) {
        if (getContext() == null) return;
        final String[] val = {existingAnswer != null && existingAnswer.answerValue != null
                ? existingAnswer.answerValue : ""};
        Button btn = new Button(getContext());
        btn.setText(val[0].isEmpty() ? "Select date & time" : val[0]);
        btn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (!val[0].isEmpty()) {
                try {
                    String[] dt = val[0].split(" ");
                    String[] dp = dt[0].split("-");
                    String[] tp = dt.length > 1 ? dt[1].split(":") : new String[]{"0", "0"};
                    cal.set(Integer.parseInt(dp[0]), Integer.parseInt(dp[1]) - 1,
                            Integer.parseInt(dp[2]),
                            Integer.parseInt(tp[0]), Integer.parseInt(tp[1]));
                } catch (Exception ignored) {}
            }
            new DatePickerDialog(requireContext(), (dp, y, m, d) ->
                new TimePickerDialog(requireContext(), (tp, h, min) -> {
                    val[0] = String.format(Locale.US, "%04d-%02d-%02d %02d:%02d", y, m + 1, d, h, min);
                    btn.setText(val[0]);
                    saveAnswer(questionId, val[0]);
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show(),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        container.addView(btn);
    }

    private void addDurationInput(int questionId, LinearLayout container,
            Answer existingAnswer, String options) {
        if (getContext() == null) return;

        // Parse format: "unit1|unit2;min;max"  (semicolons separate sections; all parts optional)
        String[] defaultUnits = {"seconds", "minutes", "hours", "days", "weeks", "months", "years"};
        String[] sections = (options != null && !options.isEmpty()) ? options.split(";", -1) : new String[0];
        String unitsPart = sections.length > 0 ? sections[0].trim() : "";
        String minPart   = sections.length > 1 ? sections[1].trim() : "";
        String maxPart   = sections.length > 2 ? sections[2].trim() : "";

        String[] units = unitsPart.isEmpty() ? defaultUnits : unitsPart.split("\\|");
        final double[] bounds = {Double.NaN, Double.NaN};
        try { if (!minPart.isEmpty()) bounds[0] = Double.parseDouble(minPart); } catch (NumberFormatException ignored) {}
        try { if (!maxPart.isEmpty()) bounds[1] = Double.parseDouble(maxPart); } catch (NumberFormatException ignored) {}

        String initValue = "";
        String initUnit = units[0];
        if (existingAnswer != null && existingAnswer.answerValue != null
                && existingAnswer.answerValue.contains("|")) {
            String[] parts = existingAnswer.answerValue.split("\\|", 2);
            initValue = parts[0];
            String savedUnit = parts[1];
            for (String u : units) { if (u.equals(savedUnit)) { initUnit = u; break; } }
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

        EditText valueEdit = new EditText(getContext());
        valueEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String hint = (!Double.isNaN(bounds[0]) && !Double.isNaN(bounds[1]))
                ? bounds[0] + " – " + bounds[1]
                : (!Double.isNaN(bounds[0]) ? "≥ " + bounds[0]
                : (!Double.isNaN(bounds[1]) ? "≤ " + bounds[1] : "Amount"));
        valueEdit.setHint(hint);
        valueEdit.setText(initValue);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        editParams.setMarginEnd(dpToPx(8));
        valueEdit.setLayoutParams(editParams);

        List<String> unitList = Arrays.asList(units);
        Spinner unitSpinner = new Spinner(getContext());
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, unitList);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);
        int initUnitIdx = unitList.indexOf(initUnit);
        if (initUnitIdx >= 0) unitSpinner.setSelection(initUnitIdx, false);
        unitSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final String[] selectedUnit = {initUnit};

        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean initialized = false;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!initialized) { initialized = true; return; }
                selectedUnit[0] = units[pos];
                String num = valueEdit.getText().toString().trim();
                if (!num.isEmpty() && isInBounds(num, bounds)) saveAnswer(questionId, num + "|" + selectedUnit[0]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        valueEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String num = s.toString().trim();
                if (num.isEmpty()) return;
                if (isInBounds(num, bounds)) {
                    valueEdit.setError(null);
                    saveAnswer(questionId, num + "|" + selectedUnit[0]);
                } else {
                    String msg = !Double.isNaN(bounds[0]) && !Double.isNaN(bounds[1])
                            ? "Must be between " + bounds[0] + " and " + bounds[1]
                            : (!Double.isNaN(bounds[0]) ? "Must be ≥ " + bounds[0] : "Must be ≤ " + bounds[1]);
                    valueEdit.setError(msg);
                }
            }
        });

        row.addView(valueEdit);
        row.addView(unitSpinner);
        container.addView(row);
    }

    private void addLocationInput(int questionId, LinearLayout container, Answer existingAnswer) {
        if (getContext() == null) return;

        // Parse stored "lat|lng|village|tehsil|district|province"
        String[] slots = {"", "", "", "", "", ""};
        if (existingAnswer != null && existingAnswer.answerValue != null
                && !existingAnswer.answerValue.isEmpty()) {
            String[] saved = existingAnswer.answerValue.split("\\|", -1);
            for (int i = 0; i < Math.min(saved.length, 6); i++) slots[i] = saved[i];
        }

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Lat / Lng row ──────────────────────────────────────────────────────
        LinearLayout coordRow = new LinearLayout(getContext());
        coordRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams coordRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        coordRowParams.bottomMargin = dpToPx(8);
        coordRow.setLayoutParams(coordRowParams);

        EditText latEdit = new EditText(getContext());
        latEdit.setHint("Latitude");
        latEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        latEdit.setText(slots[0]);
        LinearLayout.LayoutParams coordFieldParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        coordFieldParams.setMarginEnd(dpToPx(4));
        latEdit.setLayoutParams(coordFieldParams);

        EditText lngEdit = new EditText(getContext());
        lngEdit.setHint("Longitude");
        lngEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        lngEdit.setText(slots[1]);
        LinearLayout.LayoutParams lngParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lngParams.setMarginEnd(dpToPx(4));
        lngEdit.setLayoutParams(lngParams);

        Button gpsBtn = new Button(getContext());
        gpsBtn.setText("Get GPS");
        gpsBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        coordRow.addView(latEdit);
        coordRow.addView(lngEdit);
        coordRow.addView(gpsBtn);

        // ── Address fields ─────────────────────────────────────────────────────
        EditText provinceEdit = makeAddressField("Province", slots[5]);
        EditText districtEdit = makeAddressField("District", slots[4]);
        EditText tehsilEdit   = makeAddressField("Tehsil",   slots[3]);
        EditText villageEdit  = makeAddressField("Village",  slots[2]);

        root.addView(coordRow);
        root.addView(provinceEdit);
        root.addView(districtEdit);
        root.addView(tehsilEdit);
        root.addView(villageEdit);
        container.addView(root);

        // ── Save helper ────────────────────────────────────────────────────────
        Runnable save = () -> saveAnswer(questionId,
                latEdit.getText().toString().trim() + "|" +
                lngEdit.getText().toString().trim() + "|" +
                villageEdit.getText().toString().trim() + "|" +
                tehsilEdit.getText().toString().trim() + "|" +
                districtEdit.getText().toString().trim() + "|" +
                provinceEdit.getText().toString().trim());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { save.run(); }
        };
        latEdit.addTextChangedListener(watcher);
        lngEdit.addTextChangedListener(watcher);
        provinceEdit.addTextChangedListener(watcher);
        districtEdit.addTextChangedListener(watcher);
        tehsilEdit.addTextChangedListener(watcher);
        villageEdit.addTextChangedListener(watcher);

        // ── GPS button ─────────────────────────────────────────────────────────
        gpsBtn.setOnClickListener(v -> {
            pendingLocationAction = () -> fetchGpsLocation(
                    latEdit, lngEdit, villageEdit, tehsilEdit, districtEdit, provinceEdit, questionId);
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                pendingLocationAction.run();
                pendingLocationAction = null;
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
    }

    private EditText makeAddressField(String hint, String value) {
        EditText et = new EditText(getContext());
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setText(value);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dpToPx(4);
        et.setLayoutParams(p);
        return et;
    }

    @SuppressLint("MissingPermission")
    private void fetchGpsLocation(EditText latEdit, EditText lngEdit,
            EditText villageEdit, EditText tehsilEdit, EditText districtEdit,
            EditText provinceEdit, int questionId) {
        LocationManager lm = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (last != null) {
            applyLocation(last, latEdit, lngEdit, villageEdit, tehsilEdit, districtEdit, provinceEdit, questionId);
            return;
        }

        Toast.makeText(getContext(), "Acquiring GPS fix…", Toast.LENGTH_SHORT).show();
        String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        LocationListener[] ref = new LocationListener[1];
        ref[0] = location -> {
            lm.removeUpdates(ref[0]);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                    applyLocation(location, latEdit, lngEdit, villageEdit, tehsilEdit,
                            districtEdit, provinceEdit, questionId));
            }
        };
        lm.requestLocationUpdates(provider, 0, 0, ref[0], Looper.getMainLooper());
    }

    private void applyLocation(Location loc, EditText latEdit, EditText lngEdit,
            EditText villageEdit, EditText tehsilEdit, EditText districtEdit,
            EditText provinceEdit, int questionId) {
        String lat = String.format(Locale.US, "%.6f", loc.getLatitude());
        String lng = String.format(Locale.US, "%.6f", loc.getLongitude());
        latEdit.setText(lat);
        lngEdit.setText(lng);
        // coords saved immediately by the TextWatcher on latEdit

        if (Geocoder.isPresent() && isNetworkAvailable()) {
            reverseGeocode(loc.getLatitude(), loc.getLongitude(),
                    villageEdit, tehsilEdit, districtEdit, provinceEdit, questionId);
        }
    }

    @SuppressWarnings("deprecation")
    private void reverseGeocode(double lat, double lng,
            EditText villageEdit, EditText tehsilEdit, EditText districtEdit,
            EditText provinceEdit, int questionId) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1, addresses -> {
                if (!addresses.isEmpty() && getActivity() != null) {
                    Address a = addresses.get(0);
                    getActivity().runOnUiThread(() -> fillAddressFields(
                            a, villageEdit, tehsilEdit, districtEdit, provinceEdit));
                }
            });
        } else {
            new Thread(() -> {
                try {
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    if (addresses != null && !addresses.isEmpty() && getActivity() != null) {
                        Address a = addresses.get(0);
                        getActivity().runOnUiThread(() -> fillAddressFields(
                                a, villageEdit, tehsilEdit, districtEdit, provinceEdit));
                    }
                } catch (IOException ignored) {}
            }).start();
        }
    }

    private void fillAddressFields(Address a, EditText villageEdit, EditText tehsilEdit,
            EditText districtEdit, EditText provinceEdit) {
        String sub = a.getSubLocality();
        String loc = a.getLocality();
        String subAdmin = a.getSubAdminArea();
        String admin = a.getAdminArea();
        if (sub != null && !sub.isEmpty())      villageEdit.setText(sub);
        else if (loc != null && !loc.isEmpty()) villageEdit.setText(loc);
        if (loc != null && !loc.isEmpty())         tehsilEdit.setText(loc);
        if (subAdmin != null && !subAdmin.isEmpty()) districtEdit.setText(subAdmin);
        if (admin != null && !admin.isEmpty())       provinceEdit.setText(admin);
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private static boolean isInBounds(String numStr, double[] bounds) {
        try {
            double v = Double.parseDouble(numStr);
            if (!Double.isNaN(bounds[0]) && v < bounds[0]) return false;
            if (!Double.isNaN(bounds[1]) && v > bounds[1]) return false;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── Sub-question card ──────────────────────────────────────────────────────

    private LinearLayout buildSubQuestionsCard(List<Question> subQs) {
        Context ctx = getContext();
        if (ctx == null) return new LinearLayout(requireContext());

        // Outer card: horizontal with left teal border strip
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dpToPx(8), 0, dpToPx(4));
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.sub_question_bg);

        // Left teal accent strip
        View borderStrip = new View(ctx);
        borderStrip.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4),
                ViewGroup.LayoutParams.MATCH_PARENT));
        borderStrip.setBackgroundColor(ContextCompat.getColor(ctx, R.color.primary));
        card.addView(borderStrip);

        // Content container
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        // Header label
        TextView header = new TextView(ctx);
        header.setText("Follow-up details:");
        header.setTypeface(header.getTypeface(), Typeface.BOLD_ITALIC);
        header.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        header.setPadding(0, 0, 0, dpToPx(4));
        content.addView(header);

        for (int i = 0; i < subQs.size(); i++) {
            if (i > 0) {
                View divider = new View(ctx);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                divParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider));
                content.addView(divider);
            }
            View subQView = LayoutInflater.from(ctx).inflate(R.layout.view_sub_question, content, false);
            TextView subQText = subQView.findViewById(R.id.sub_question_text);
            subQText.setText(subQs.get(i).getQuestionText());
            content.addView(subQView);
            loadAndPopulateSubQuestion(subQs.get(i), subQView);
        }

        card.addView(content);
        return card;
    }

    private void loadAndPopulateSubQuestion(Question subQ, View subQView) {
        LinearLayout subAnswersContainer = subQView.findViewById(R.id.sub_answers_container);
        EditText subRemarksEditText = subQView.findViewById(R.id.sub_remarks_edit_text);
        LinearLayout subAttachmentsContainer = subQView.findViewById(R.id.sub_attachments_container);
        ImageButton subPhotoBtn = subQView.findViewById(R.id.sub_add_photo_button);
        ImageButton subVideoBtn = subQView.findViewById(R.id.sub_add_video_button);
        ImageButton subAudioBtn = subQView.findViewById(R.id.sub_add_audio_button);
        ImageButton subFileBtn = subQView.findViewById(R.id.sub_attach_file_button);

        attachmentsContainerMap.put(subQ.getId(), subAttachmentsContainer);

        subPhotoBtn.setOnClickListener(v -> {
            currentMediaQuestionId = subQ.getId();
            requestedMedia = "PHOTO";
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA});
        });
        subVideoBtn.setOnClickListener(v -> {
            currentMediaQuestionId = subQ.getId();
            requestedMedia = "VIDEO";
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        });
        subAudioBtn.setOnClickListener(v -> {
            currentMediaQuestionId = subQ.getId();
            requestedMedia = "AUDIO";
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
        });
        subFileBtn.setOnClickListener(v -> {
            currentMediaQuestionId = subQ.getId();
            getContentLauncher.launch("*/*");
        });

        repository.getAnswer(surveyId, subQ.getId(), subAnswer -> {
            repository.getAllPreviousAnswersForQuestion(surveyId, subQ.getId(), prevAnswers -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getContext() == null) return;
                    if (subAnswer != null) subRemarksEditText.setText(subAnswer.remarks);
                    subRemarksEditText.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                        @Override public void afterTextChanged(Editable s) {
                            saveRemarks(subQ.getId(), s.toString());
                        }
                    });
                    populateAnswerControls(subQ.getId(), subQ.getAnswerType(),
                            subQ.getAnswerOptions(), subAnswersContainer, subAnswer,
                            prevAnswers, Collections.emptyList());
                    loadAttachments(subQ.getId(), subAttachmentsContainer);
                });
            });
        });
    }

    // ── Answer & remarks persistence ──────────────────────────────────────────

    private void saveAnswer(int questionId, String value, @Nullable Runnable onComplete) {
        repository.getAnswer(surveyId, questionId, existingAnswer -> {
            Answer answerToSave = existingAnswer;
            if (answerToSave == null) {
                answerToSave = new Answer();
                answerToSave.surveyId = surveyId;
                answerToSave.questionId = questionId;
            }
            answerToSave.answerValue = value;
            repository.upsertAnswer(answerToSave, () -> {
                if (listener != null) listener.onAnswerSaved();
                if (onComplete != null) onComplete.run();
            });
        });
    }

    private void saveAnswer(int questionId, String value) { saveAnswer(questionId, value, null); }
    private void saveAnswer(String value) { saveAnswer(question.getId(), value, null); }
    private void saveAnswer(String value, @Nullable Runnable onComplete) {
        saveAnswer(question.getId(), value, onComplete);
    }

    private void saveRemarks(int questionId, String remarks) {
        repository.getAnswer(surveyId, questionId, existingAnswer -> {
            Answer answerToSave = existingAnswer;
            if (answerToSave == null) {
                answerToSave = new Answer();
                answerToSave.surveyId = surveyId;
                answerToSave.questionId = questionId;
            }
            answerToSave.remarks = remarks;
            repository.upsertAnswer(answerToSave, () -> {
                if (listener != null) listener.onAnswerSaved();
            });
        });
    }

    private void saveRemarks(String remarks) { saveRemarks(question.getId(), remarks); }

    // ── Helpers ────────────────────────────────────────────────────────────────

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private int parseIntOrDefault(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String getFileNameFromUri(Uri uri) {
        if (getContext() == null) return null;
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private File copyUriToInternalStorage(Uri uri) throws IOException {
        if (getContext() == null) return null;
        try (java.io.InputStream inputStream = getContext().getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            String originalFileName = getFileNameFromUri(uri);
            if (originalFileName == null) {
                String mimeType = getContext().getContentResolver().getType(uri);
                String extension = ".bin";
                if (mimeType != null) {
                    String extFromMime = android.webkit.MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mimeType);
                    if (extFromMime != null) extension = "." + extFromMime;
                }
                originalFileName = "attached_file" + extension;
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String uniqueFileName = timeStamp + "_" + originalFileName;
            File destinationFile = new File(getContext().getFilesDir(), uniqueFileName);
            try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destinationFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) outputStream.write(buffer, 0, length);
            }
            return destinationFile;
        }
    }
}
