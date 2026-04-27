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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.models.GroupMessage;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.utils.NotificationUtil;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupChatFragment extends Fragment {

    public static final String ARG_GROUP_ID      = "group_id";
    public static final String ARG_GROUP_NAME    = "group_name";
    public static final String ARG_GROUP_CREATOR = "group_creator_id";

    public static GroupChatFragment newInstance(long groupId, String groupName, long creatorId) {
        GroupChatFragment f = new GroupChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_GROUP_NAME, groupName);
        args.putLong(ARG_GROUP_CREATOR, creatorId);
        f.setArguments(args);
        return f;
    }

    // Élément unifié du flux (message OU post)
    static class ChatItem implements Comparable<ChatItem> {
        static final int TYPE_MESSAGE = 0;
        static final int TYPE_POST    = 1;
        int type;
        GroupMessage message;
        Photo post;
        String date;

        static ChatItem fromMessage(GroupMessage m) {
            ChatItem item = new ChatItem();
            item.type    = TYPE_MESSAGE;
            item.message = m;
            item.date    = m.date != null ? m.date : "";
            return item;
        }

        static ChatItem fromPost(Photo p) {
            ChatItem item = new ChatItem();
            item.type = TYPE_POST;
            item.post = p;
            item.date = p.getDate() != null ? p.getDate() : "";
            return item;
        }

        @Override
        public int compareTo(ChatItem other) {
            return this.date.compareTo(other.date);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_chat, container, false);

        long groupId   = getArguments() != null ? getArguments().getLong(ARG_GROUP_ID) : -1;
        String groupName = getArguments() != null ? getArguments().getString(ARG_GROUP_NAME, "Groupe") : "Groupe";
        long creatorId = getArguments() != null ? getArguments().getLong(ARG_GROUP_CREATOR, -1) : -1;

        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        SessionManager session = new SessionManager(requireContext());
        boolean isCreator = session.isLoggedIn() && session.getUserId() == creatorId;

        ((TextView) view.findViewById(R.id.tv_chat_group_name)).setText("💬 " + groupName);
        view.findViewById(R.id.btn_chat_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        // ── Section demandes (créateur uniquement) ─────────────────────────
        View layoutRequests = view.findViewById(R.id.layout_requests);
        TextView tvBadge    = view.findViewById(R.id.tv_requests_badge);
        RecyclerView rvReqs = view.findViewById(R.id.rv_requests);
        rvReqs.setLayoutManager(new LinearLayoutManager(getContext()));
        RequestAdapter reqAdapter = new RequestAdapter(viewModel, groupId);
        rvReqs.setAdapter(reqAdapter);

        if (isCreator) {
            viewModel.getPendingCountForGroup(groupId).observe(getViewLifecycleOwner(), count -> {
                boolean hasPending = count != null && count > 0;
                tvBadge.setVisibility(hasPending ? View.VISIBLE : View.GONE);
                if (hasPending) tvBadge.setText("● " + count + " demande" + (count > 1 ? "s" : ""));
                layoutRequests.setVisibility(hasPending ? View.VISIBLE : View.GONE);
            });
            viewModel.getPendingForGroup(groupId).observe(getViewLifecycleOwner(), reqAdapter::setRequests);
        }

        // ── Flux unifié messages + posts ───────────────────────────────────
        RecyclerView rv = view.findViewById(R.id.rv_chat_messages);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setStackFromEnd(true); // scroll vers le bas automatiquement
        rv.setLayoutManager(llm);
        ChatAdapter chatAdapter = new ChatAdapter(session.getUserId(), session.getUsername());
        rv.setAdapter(chatAdapter);

        // MediatorLiveData pour fusionner messages + posts
        MediatorLiveData<List<ChatItem>> merged = new MediatorLiveData<>();
        final List<GroupMessage>[] latestMessages = new List[]{new ArrayList<>()};
        final List<Photo>[] latestPosts = new List[]{new ArrayList<>()};

        merged.addSource(viewModel.getGroupMessages(groupId), messages -> {
            latestMessages[0] = messages != null ? messages : new ArrayList<>();
            merged.setValue(buildFeed(latestMessages[0], latestPosts[0]));
        });
        merged.addSource(viewModel.getPhotosByGroup(groupId), posts -> {
            latestPosts[0] = posts != null ? posts : new ArrayList<>();
            merged.setValue(buildFeed(latestMessages[0], latestPosts[0]));
        });

        merged.observe(getViewLifecycleOwner(), items -> {
            chatAdapter.setItems(items);
            if (!items.isEmpty()) rv.scrollToPosition(items.size() - 1);
        });

        // ── Envoi de message ───────────────────────────────────────────────
        EditText etInput = view.findViewById(R.id.et_chat_input);
        view.findViewById(R.id.btn_chat_send).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour envoyer un message.", Toast.LENGTH_SHORT).show();
                return;
            }
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) return;

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            GroupMessage msg = new GroupMessage();
            msg.groupId    = groupId;
            msg.userId     = session.getUserId();
            msg.authorName = session.getUsername();
            msg.message    = text;
            msg.date       = date;
            viewModel.sendGroupMessage(msg);

            AppNotification notif = new AppNotification();
            notif.targetUserId = session.getUserId();
            notif.type    = "GROUP_MESSAGE";
            notif.message = session.getUsername() + " dans " + groupName + " : " + text;
            notif.groupId = groupId;
            notif.date    = date;
            viewModel.insertAppNotification(notif);

            NotificationUtil.showNotification(requireContext(),
                    "Nouveau message — " + groupName,
                    session.getUsername() + " : " + text);

            etInput.setText("");
        });

        return view;
    }

    /** Fusionne messages et posts triés par date */
    private List<ChatItem> buildFeed(List<GroupMessage> messages, List<Photo> posts) {
        List<ChatItem> items = new ArrayList<>();
        for (GroupMessage m : messages) items.add(ChatItem.fromMessage(m));
        for (Photo p : posts) items.add(ChatItem.fromPost(p));
        Collections.sort(items);
        return items;
    }

    // ── Adapter demandes ──────────────────────────────────────────────────

    static class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.RVH> {
        private List<GroupMember> requests = new ArrayList<>();
        private final SharedViewModel viewModel;
        private final long groupId;

        RequestAdapter(SharedViewModel vm, long gid) { this.viewModel = vm; this.groupId = gid; }

        static class RVH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvName, btnAccept, btnReject;
            RVH(View v) {
                super(v);
                tvAvatar  = v.findViewById(R.id.tv_req_avatar);
                tvName    = v.findViewById(R.id.tv_req_name);
                btnAccept = v.findViewById(R.id.btn_accept);
                btnReject = v.findViewById(R.id.btn_reject);
            }
        }

        @NonNull @Override
        public RVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RVH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_join_request, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RVH h, int position) {
            GroupMember m = requests.get(position);
            String name = m.userName != null ? m.userName : "Utilisateur #" + m.userId;
            h.tvAvatar.setText(!name.isEmpty() ? String.valueOf(name.charAt(0)).toUpperCase() : "?");
            h.tvName.setText(name);
            h.btnAccept.setOnClickListener(v -> {
                viewModel.acceptJoinRequest(groupId, m.userId);
                AppNotification notif = new AppNotification();
                notif.targetUserId = m.userId;
                notif.type    = "JOIN_ACCEPTED";
                notif.message = "Votre demande d'adhésion a été acceptée !";
                notif.groupId = groupId;
                notif.date    = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                viewModel.insertAppNotification(notif);
                Toast.makeText(v.getContext(), name + " accepté !", Toast.LENGTH_SHORT).show();
            });
            h.btnReject.setOnClickListener(v -> {
                viewModel.rejectOrLeaveGroup(groupId, m.userId);
                Toast.makeText(v.getContext(), "Demande refusée.", Toast.LENGTH_SHORT).show();
            });
        }

        @Override public int getItemCount() { return requests.size(); }

        void setRequests(List<GroupMember> list) {
            this.requests = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }

    // ── Adapter flux unifié ───────────────────────────────────────────────

    static class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<ChatItem> items = new ArrayList<>();
        private final long currentUserId;
        private final String currentUsername;

        ChatAdapter(long uid, String username) {
            this.currentUserId   = uid;
            this.currentUsername = username != null ? username : "";
        }

        // ViewHolder message texte
        static class MessageVH extends RecyclerView.ViewHolder {
            android.widget.LinearLayout bubble;
            TextView tvAuthor, tvText, tvDate;
            MessageVH(View v) {
                super(v);
                bubble   = v.findViewById(R.id.msg_bubble);
                tvAuthor = v.findViewById(R.id.tv_msg_author);
                tvText   = v.findViewById(R.id.tv_msg_text);
                tvDate   = v.findViewById(R.id.tv_msg_date);
            }
        }

        // ViewHolder post photo
        static class PostVH extends RecyclerView.ViewHolder {
            android.widget.FrameLayout cardContainer;
            androidx.cardview.widget.CardView card;
            ImageView ivImage;
            View vColor;
            TextView tvTitle, tvMeta, tvAuthor;
            PostVH(View v) {
                super(v);
                cardContainer = (android.widget.FrameLayout) v;
                card     = v.findViewById(R.id.card_chat_post);
                ivImage  = v.findViewById(R.id.iv_chat_post_image);
                vColor   = v.findViewById(R.id.v_chat_post_color);
                tvTitle  = v.findViewById(R.id.tv_chat_post_title);
                tvMeta   = v.findViewById(R.id.tv_chat_post_meta);
                tvAuthor = v.findViewById(R.id.tv_chat_post_author);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == ChatItem.TYPE_POST) {
                return new PostVH(inf.inflate(R.layout.item_chat_post, parent, false));
            }
            return new MessageVH(inf.inflate(R.layout.item_group_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatItem item = items.get(position);
            if (item.type == ChatItem.TYPE_MESSAGE) {
                bindMessage((MessageVH) holder, item.message);
            } else {
                bindPost((PostVH) holder, item.post);
            }
        }

        private void bindMessage(MessageVH h, GroupMessage m) {
            boolean mine = m.userId == currentUserId;

            android.view.ViewGroup.LayoutParams lp = h.bubble.getLayoutParams();
            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                ((android.widget.FrameLayout.LayoutParams) lp).gravity =
                        mine ? Gravity.END : Gravity.START;
                h.bubble.setLayoutParams(lp);
            }
            int bgColor = mine
                    ? h.bubble.getContext().getResources().getColor(R.color.teal, null)
                    : h.bubble.getContext().getResources().getColor(R.color.sand, null);
            h.bubble.setBackgroundColor(bgColor);
            h.tvAuthor.setTextColor(mine
                    ? h.tvAuthor.getContext().getResources().getColor(R.color.default_white, null)
                    : h.tvAuthor.getContext().getResources().getColor(R.color.teal, null));
            h.tvAuthor.setText(mine ? "Moi" : m.authorName);
            h.tvText.setTextColor(mine
                    ? h.tvText.getContext().getResources().getColor(R.color.default_white, null)
                    : h.tvText.getContext().getResources().getColor(R.color.ink, null));
            h.tvText.setText(m.message);
            h.tvDate.setText(m.date);
        }

        private void bindPost(PostVH h, Photo p) {
            boolean mine = currentUsername.equalsIgnoreCase(p.getAuthor() != null ? p.getAuthor() : "");
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) h.card.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.card.setLayoutParams(lp);

            h.tvTitle.setText(p.getTitle());
            h.tvMeta.setText("📍 " + p.getLocation());
            h.tvAuthor.setText(mine ? "Moi" : "Publié par @" + p.getAuthor());

            String uri = p.getImageUri();
            if (uri != null && !uri.isEmpty()) {
                h.ivImage.setVisibility(View.VISIBLE);
                h.vColor.setVisibility(View.GONE);
                Glide.with(h.ivImage.getContext()).load(Uri.parse(uri)).centerCrop().into(h.ivImage);
            } else {
                h.ivImage.setVisibility(View.GONE);
                h.vColor.setVisibility(View.VISIBLE);
            }

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
                v.getContext().startActivity(intent);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        void setItems(List<ChatItem> list) {
            this.items = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
