package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private ListenerRegistration notifListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        SessionManager session = new SessionManager(requireContext());
        long userId = session.getUserId();

        RecyclerView rv = view.findViewById(R.id.rv_notifications_feed);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        NotifAdapter adapter = new NotifAdapter(viewModel, this);
        rv.setAdapter(adapter);

        viewModel.getAppNotificationsForUser(userId).observe(getViewLifecycleOwner(), adapter::setNotifs);

        view.findViewById(R.id.btn_clear_notifs).setOnClickListener(v ->
                viewModel.clearNotificationsForUser(userId));

        if (session.isLoggedIn()) {
            notifListener = FirebaseRepository.getInstance().listenToNotifications(
                    session.getUsername(), AppDatabase.getInstance(requireContext()), userId);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notifListener != null) { notifListener.remove(); notifListener = null; }
    }

    static class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.NVH> {
        private List<AppNotification> notifs = new ArrayList<>();
        private final SharedViewModel viewModel;
        private final Fragment fragment;

        NotifAdapter(SharedViewModel vm, Fragment f) {
            this.viewModel = vm;
            this.fragment  = f;
        }

        static class NVH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvMessage, tvDate;
            View dotUnread;
            NVH(View v) {
                super(v);
                tvIcon    = v.findViewById(R.id.tv_notif_icon);
                tvMessage = v.findViewById(R.id.tv_notif_message);
                tvDate    = v.findViewById(R.id.tv_notif_date);
                dotUnread = v.findViewById(R.id.dot_unread);
            }
        }

        @NonNull @Override
        public NVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notif_feed, parent, false);
            return new NVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NVH h, int position) {
            AppNotification n = notifs.get(position);

            switch (n.type != null ? n.type : "") {
                case "LIKE":          h.tvIcon.setText("❤️"); break;
                case "COMMENT":       h.tvIcon.setText("💬"); break;
                case "GROUP_MESSAGE": h.tvIcon.setText("👥"); break;
                case "JOIN_REQUEST":  h.tvIcon.setText("🔔"); break;
                case "JOIN_ACCEPTED": h.tvIcon.setText("✅"); break;
                default:              h.tvIcon.setText("🔔"); break;
            }
            h.tvMessage.setText(n.message);
            h.tvDate.setText(n.date);
            h.dotUnread.setVisibility(n.isRead ? View.INVISIBLE : View.VISIBLE);

            h.itemView.setOnClickListener(v -> {
                viewModel.markNotificationRead(n.id);

                switch (n.type != null ? n.type : "") {
                    case "LIKE":
                    case "COMMENT":

                        if (n.photoId > 0) {
                            viewModel.getPhotoById(n.photoId, photo -> {
                                if (photo == null) return;
                                Intent intent = new Intent(v.getContext(), PhotoDetailActivity.class);
                                intent.putExtra(PhotoDetailActivity.EXTRA_PHOTO_ID,  photo.getId());
                                intent.putExtra(PhotoDetailActivity.EXTRA_TITLE,     photo.getTitle());
                                intent.putExtra(PhotoDetailActivity.EXTRA_AUTHOR,    photo.getAuthor());
                                intent.putExtra(PhotoDetailActivity.EXTRA_DATE,      photo.getDate());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LOCATION,  photo.getLocation());
                                intent.putExtra(PhotoDetailActivity.EXTRA_CATEGORY,  photo.getCategory());
                                intent.putExtra(PhotoDetailActivity.EXTRA_TAGS,      photo.getTags());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LIKES,     photo.getLikes());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LAT,       photo.getLatitude());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LNG,       photo.getLongitude());
                                intent.putExtra(PhotoDetailActivity.EXTRA_IMAGE_URI, photo.getImageUri());
                                v.getContext().startActivity(intent);
                            });
                        }
                        break;

                    case "JOIN_REQUEST":

                        if (n.groupId > 0) {
                            viewModel.getGroupById(n.groupId, group -> {
                                if (group == null) {
                                    fragment.requireActivity().runOnUiThread(() ->
                                            Toast.makeText(v.getContext(), "Groupe introuvable.", Toast.LENGTH_SHORT).show());
                                    return;
                                }
                                fragment.requireActivity().runOnUiThread(() ->
                                        fragment.requireActivity().getSupportFragmentManager()
                                                .beginTransaction()
                                                .replace(R.id.fragment_container,
                                                        GroupChatFragment.newInstance(group.id, group.name, group.creatorId))
                                                .addToBackStack(null)
                                                .commit());
                            });
                        }
                        break;

                    case "JOIN_ACCEPTED":
                    case "GROUP_MESSAGE":

                        if (n.groupId > 0) {
                            viewModel.getGroupById(n.groupId, group -> {
                                if (group == null) return;
                                fragment.requireActivity().runOnUiThread(() ->
                                        fragment.requireActivity().getSupportFragmentManager()
                                                .beginTransaction()
                                                .replace(R.id.fragment_container,
                                                        GroupChatFragment.newInstance(group.id, group.name, group.creatorId))
                                                .addToBackStack(null)
                                                .commit());
                            });
                        }
                        break;

                    default:
                        break;
                }
            });
        }

        @Override public int getItemCount() { return notifs.size(); }

        void setNotifs(List<AppNotification> list) {
            this.notifs = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
