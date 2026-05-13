package com.example.cref_wss_01;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ThankYouFragment extends Fragment {

    private OnCompletionActionsListener listener;

    public interface OnCompletionActionsListener {
        void onExportAndClose();
        void onShareAndClose();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnCompletionActionsListener) {
            listener = (OnCompletionActionsListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_thank_you, container, false);

        Button exportButton = view.findViewById(R.id.export_close_button);
        Button shareButton = view.findViewById(R.id.share_close_button);

        exportButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExportAndClose();
            }
        });

        shareButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShareAndClose();
            }
        });

        return view;
    }
}
    