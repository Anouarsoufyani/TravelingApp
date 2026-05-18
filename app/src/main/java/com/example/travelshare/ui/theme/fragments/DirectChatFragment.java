package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.models.GroupMessage;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DirectChatFragment extends Fragment {

    private static final String ARG_TARGET_USER = "target_username";

    private String targetUsername;
    private String currentUsername;
    private ChatAdapter adapter;
    private ListenerRegistration chatListener;
    private SharedViewModel sharedViewModel;

    public static DirectChatFragment newInstance(String targetUsername) {
        DirectChatFragment f = new DirectChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_USER, targetUsername);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_chat, container, false);

        targetUsername = getArguments() != null ? getArguments().getString(ARG_TARGET_USER) : "";
        SessionManager session = new SessionManager(requireContext());
        currentUsername = session.getUsername();
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        ((TextView) view.findViewById(R.id.tv_chat_group_name)).setText("👤 " + targetUsername);
        view.findViewById(R.id.btn_chat_back).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_invite_group).setVisibility(View.GONE);

        RecyclerView rv = view.findViewById(R.id.rv_chat_messages);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatAdapter(currentUsername, sharedViewModel, this);
        rv.setAdapter(adapter);

        chatListener = FirebaseRepository.getInstance().listenToDirectMessages(currentUsername, targetUsername, messages -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                adapter.setMessages(messages);
                if (messages != null && !messages.isEmpty())
                    rv.scrollToPosition(messages.size() - 1);
            });
        });

        EditText etInput = view.findViewById(R.id.et_chat_input);
        view.findViewById(R.id.btn_chat_send).setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) return;

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            FirebaseRepository.getInstance().saveDirectMessage(currentUsername, targetUsername, text, date, 0, 0, -1, null);
            etInput.setText("");
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatListener != null) chatListener.remove();
    }

    static class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        static final int TYPE_MESSAGE = 0;
        static final int TYPE_SHARED  = 1;
        static final int TYPE_PLAN    = 2;
        static final int TYPE_INVITE  = 3;

        private List<GroupMessage> messages = new ArrayList<>();
        private final String currentUsername;
        private final SharedViewModel viewModel;
        private final Fragment fragment;

        ChatAdapter(String currentUsername, SharedViewModel vm, Fragment f) {
            this.currentUsername = currentUsername;
            this.viewModel = vm;
            this.fragment = f;
        }

        void setMessages(List<GroupMessage> list) { this.messages = list; notifyDataSetChanged(); }

        @Override
        public int getItemViewType(int position) {
            GroupMessage m = messages.get(position);
            if (m.photoId > 0) return TYPE_SHARED;
            if (m.planId > 0)  return TYPE_PLAN;
            if (m.invitationGroupId > 0) return TYPE_INVITE;
            return TYPE_MESSAGE;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SHARED || viewType == TYPE_PLAN || viewType == TYPE_INVITE) {
                return new SharedVH(inf.inflate(R.layout.item_shared_photo, parent, false));
            }
            return new MessageVH(inf.inflate(R.layout.item_group_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            GroupMessage m = messages.get(position);
            boolean mine = currentUsername.equalsIgnoreCase(m.authorName);

            if (holder instanceof MessageVH) {
                bindMessage((MessageVH) holder, m, mine);
            } else {
                bindShared((SharedVH) holder, m, mine);
            }
        }

        private void bindMessage(MessageVH h, GroupMessage m, boolean mine) {
            android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) h.card.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.card.setLayoutParams(lp);
            h.card.setCardBackgroundColor(h.itemView.getContext().getResources().getColor(mine ? R.color.coral : R.color.surface_field, null));
            h.tvText.setText(m.message);
            h.tvDate.setText(m.date);
        }

        private void bindShared(SharedVH h, GroupMessage m, boolean mine) {
            android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) h.card.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.card.setLayoutParams(lp);

            h.tvTitle.setText(m.message);
            h.tvAuthor.setText(mine ? "Moi" : m.authorName);

            if (m.photoId > 0) {
                h.ivImage.setVisibility(View.VISIBLE);
                h.tvLocation.setVisibility(View.VISIBLE);
                h.btnCTA.setText("Voir la photo →");
                viewModel.getPhotoById(m.photoId, p -> {
                    if (p != null && fragment.isAdded()) {
                        fragment.requireActivity().runOnUiThread(() -> {
                            h.tvLocation.setText(p.getLocation());
                            if (p.getImageUri() != null)
                                Glide.with(fragment).load(Uri.parse(p.getImageUri())).centerCrop().into(h.ivImage);
                            
                            h.itemView.setOnClickListener(v -> {
                                Intent intent = new Intent(v.getContext(), PhotoDetailActivity.class);
                                intent.putExtra(PhotoDetailActivity.EXTRA_PHOTO_ID,  p.getId());
                                intent.putExtra(PhotoDetailActivity.EXTRA_TITLE,     p.getTitle());
                                intent.putExtra(PhotoDetailActivity.EXTRA_AUTHOR,    p.getAuthor());
                                intent.putExtra(PhotoDetailActivity.EXTRA_DATE,      p.getDate());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LOCATION,  p.getLocation());
                                intent.putExtra(PhotoDetailActivity.EXTRA_CATEGORY,  p.getCategory());
                                intent.putExtra(PhotoDetailActivity.EXTRA_TAGS,      p.getTags());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LIKES,     p.getLikes());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LAT,       p.getLatitude());
                                intent.putExtra(PhotoDetailActivity.EXTRA_LNG,       p.getLongitude());
                                intent.putExtra(PhotoDetailActivity.EXTRA_IMAGE_URI, p.getImageUri());
                                intent.putExtra(PhotoDetailActivity.EXTRA_VOICE_URI, p.getVoiceUri());
                                v.getContext().startActivity(intent);
                            });
                        });
                    }
                });
            } else if (m.planId > 0) {
                h.ivImage.setVisibility(View.GONE);
                h.tvLocation.setVisibility(View.GONE);
                h.btnCTA.setText("Voir le parcours →");
                h.itemView.setOnClickListener(v -> {
                    fragment.requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, PlanDetailFragment.newInstance(m.planId))
                            .addToBackStack(null).commit();
                });
            } else if (m.invitationGroupId > 0) {
                h.ivImage.setVisibility(View.GONE);
                h.tvLocation.setVisibility(View.VISIBLE);
                h.tvLocation.setText("Groupe : " + m.invitationGroupName);
                h.btnCTA.setText(mine ? "Invitation envoyée" : "Rejoindre le groupe →");
                h.itemView.setOnClickListener(v -> {
                    if (mine) return;
                    FirebaseRepository.getInstance().acceptGroupInvitation(m.invitationGroupName, currentUsername);
                    Toast.makeText(v.getContext(), "Vous avez rejoint \"" + m.invitationGroupName + "\"", Toast.LENGTH_SHORT).show();
                    h.btnCTA.setText("Rejoint ✓");
                    h.itemView.setOnClickListener(null);
                });
            }
        }

        @Override public int getItemCount() { return messages.size(); }

        static class MessageVH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView tvText, tvDate;
            MessageVH(View v) {
                super(v);
                card   = v.findViewById(R.id.msg_card);
                tvText = v.findViewById(R.id.tv_msg_text);
                tvDate = v.findViewById(R.id.tv_msg_date);
                v.findViewById(R.id.tv_msg_author).setVisibility(View.GONE);
            }
        }

        static class SharedVH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            ImageView ivImage;
            TextView tvTitle, tvLocation, tvAuthor, btnCTA;
            SharedVH(View v) {
                super(v);
                card = v.findViewById(R.id.card_shared);
                ivImage = v.findViewById(R.id.iv_shared_image);
                tvTitle = v.findViewById(R.id.tv_shared_title);
                tvLocation = v.findViewById(R.id.tv_shared_location);
                tvAuthor = v.findViewById(R.id.tv_shared_author);
                
                // Hack: find the "Voir →" textview to use as CTA
                // In item_shared_photo it's the last TextView in the last LinearLayout
                ViewGroup parent = (ViewGroup) tvAuthor.getParent();
                if (parent.getChildCount() > 1) {
                    btnCTA = (TextView) parent.getChildAt(1);
                }
            }
        }
    }
}
