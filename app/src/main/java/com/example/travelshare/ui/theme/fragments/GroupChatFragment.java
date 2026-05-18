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
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.models.GroupMessage;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.utils.NotificationUtil;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.firebase.firestore.ListenerRegistration;

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
    public static final String ARG_GROUP_CREATOR_USERNAME = "group_creator_username";

    private ListenerRegistration chatListener;
    private ListenerRegistration membersListener;

    public static GroupChatFragment newInstance(long groupId, String groupName, long creatorId) {
        return newInstance(groupId, groupName, creatorId, null);
    }

    public static GroupChatFragment newInstance(long groupId, String groupName, long creatorId,
                                                @Nullable String creatorUsername) {
        GroupChatFragment f = new GroupChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_GROUP_NAME, groupName);
        args.putLong(ARG_GROUP_CREATOR, creatorId);
        args.putString(ARG_GROUP_CREATOR_USERNAME, creatorUsername);
        f.setArguments(args);
        return f;
    }

    static class ChatItem implements Comparable<ChatItem> {
        static final int TYPE_MESSAGE = 0;
        static final int TYPE_POST    = 1;
        static final int TYPE_SHARED  = 2;
        static final int TYPE_PLAN    = 3;
        int type;
        GroupMessage message;
        Photo post;
        String date;

        static ChatItem fromMessage(GroupMessage m) {
            ChatItem item = new ChatItem();
            if (m.photoId > 0) item.type = TYPE_SHARED;
            else if (m.planId > 0) item.type = TYPE_PLAN;
            else item.type = TYPE_MESSAGE;
            
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
        String creatorUsername = getArguments() != null
                ? getArguments().getString(ARG_GROUP_CREATOR_USERNAME, null) : null;

        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        SessionManager session = new SessionManager(requireContext());
        boolean isCreator = session.isLoggedIn()
                && ((creatorUsername != null && creatorUsername.equalsIgnoreCase(session.getUsername()))
                || session.getUserId() == creatorId);

        TextView tvGroupName = view.findViewById(R.id.tv_chat_group_name);
        tvGroupName.setText("💬 " + groupName);
        tvGroupName.setOnClickListener(v -> showGroupDetailsDialog(groupName, creatorUsername));

        view.findViewById(R.id.btn_chat_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.btn_invite_group).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour inviter un membre.", Toast.LENGTH_SHORT).show();
                return;
            }
            showInviteDialog(groupName, groupId, session.getUsername());
        });
        view.findViewById(R.id.btn_group_menu).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour gérer ce groupe.", Toast.LENGTH_SHORT).show();
                return;
            }
            showGroupActionsDialog(groupId, groupName, creatorId, creatorUsername, isCreator, viewModel, session);
        });

        View layoutRequests = view.findViewById(R.id.layout_requests);
        TextView tvBadge    = view.findViewById(R.id.tv_requests_badge);
        RecyclerView rvReqs = view.findViewById(R.id.rv_requests);
        rvReqs.setLayoutManager(new LinearLayoutManager(getContext()));
        RequestAdapter reqAdapter = new RequestAdapter(viewModel, groupId, groupName);
        rvReqs.setAdapter(reqAdapter);

        if (isCreator) {
            membersListener = FirebaseRepository.getInstance().listenToPendingMembers(groupName, requests -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    int count = requests != null ? requests.size() : 0;
                    boolean hasPending = count > 0;
                    tvBadge.setVisibility(hasPending ? View.VISIBLE : View.GONE);
                    if (hasPending) tvBadge.setText("● " + count + " demande" + (count > 1 ? "s" : ""));
                    layoutRequests.setVisibility(hasPending ? View.VISIBLE : View.GONE);
                    reqAdapter.setRequests(requests);
                });
            });
        }

        RecyclerView rv = view.findViewById(R.id.rv_chat_messages);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setStackFromEnd(true);
        rv.setLayoutManager(llm);
        ChatAdapter chatAdapter = new ChatAdapter(session.getUserId(), session.getUsername());
        rv.setAdapter(chatAdapter);

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

        chatListener = FirebaseRepository.getInstance().listenToMessages(
                groupName, AppDatabase.getInstance(requireContext()), groupId);

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
            FirebaseRepository.getInstance().saveMessage(groupName, session.getUsername(), text, date);

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

    private void showGroupDetailsDialog(String groupName, String creator) {
        FirebaseRepository.getInstance().getGroupMembers(groupName, members -> {
            if (!members.contains(creator) && creator != null) {
                members.add(0, creator + " (👑)");
            }

            String[] memberArray = members.toArray(new String[0]);

            requireActivity().runOnUiThread(() -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Membres de " + groupName)
                        .setItems(memberArray, (dialog, which) -> {
                            String selected = memberArray[which].replace(" (👑)", "");
                            android.content.Intent intent = new android.content.Intent(getContext(), com.example.travelshare.ui.UserProfileActivity.class);
                            intent.putExtra(com.example.travelshare.ui.UserProfileActivity.EXTRA_USERNAME, selected);
                            startActivity(intent);
                        })
                        .setPositiveButton("Fermer", null)
                        .show();
            });
        });
    }

    private void showGroupActionsDialog(long groupId, String groupName, long creatorId,
                                        @Nullable String creatorUsername, boolean isCreator,
                                        SharedViewModel viewModel, SessionManager session) {
        if (!isAdded()) return;

        if (isCreator) {
            String[] actions = {"Modifier le nom", "Supprimer le groupe"};
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(groupName)
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            showRenameGroupDialog(groupId, groupName, creatorId, creatorUsername);
                        } else {
                            confirmDeleteGroup(groupId, groupName);
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(groupName)
                .setItems(new String[]{"Quitter le groupe"}, (dialog, which) ->
                        confirmLeaveGroup(groupId, groupName, viewModel, session))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showRenameGroupDialog(long groupId, String currentName, long creatorId,
                                       @Nullable String creatorUsername) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(currentName);
        input.setSelection(input.getText().length());
        input.setPadding(48, 16, 48, 0);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Modifier le nom")
                .setView(input)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(getContext(), "Le nom ne peut pas être vide.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseRepository.getInstance().renameGroup(currentName, newName, success -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!success) {
                                Toast.makeText(getContext(),
                                        "Impossible de renommer ce groupe. Le nom existe peut-être déjà.",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            Toast.makeText(getContext(), "Groupe renommé.", Toast.LENGTH_SHORT).show();
                            SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                            viewModel.updateLocalGroupName(groupId, newName);
                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.fragment_container,
                                            GroupChatFragment.newInstance(groupId, newName, creatorId, creatorUsername))
                                    .addToBackStack(null)
                                    .commit();
                        });
                    });
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void confirmDeleteGroup(long groupId, String groupName) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Supprimer le groupe ?")
                .setMessage("Cette action supprimera le groupe, ses membres et ses messages partagés.")
                .setPositiveButton("Supprimer", (dialog, which) ->
                        FirebaseRepository.getInstance().deleteGroup(groupName, success -> {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                if (!success) {
                                    Toast.makeText(getContext(), "Suppression impossible.", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Toast.makeText(getContext(), "Groupe supprimé.", Toast.LENGTH_SHORT).show();
                                SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                                viewModel.deleteLocalGroup(groupId);
                                requireActivity().getSupportFragmentManager().popBackStack();
                            });
                        }))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void confirmLeaveGroup(long groupId, String groupName, SharedViewModel viewModel,
                                   SessionManager session) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Quitter le groupe ?")
                .setMessage("Vous ne verrez plus cette conversation dans vos groupes.")
                .setPositiveButton("Quitter", (dialog, which) -> {
                    FirebaseRepository.getInstance().deleteGroupMember(groupName, session.getUsername());
                    viewModel.rejectOrLeaveGroup(groupId, session.getUserId());
                    Toast.makeText(getContext(), "Vous avez quitté le groupe.", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showInviteDialog(String groupName, long groupId, String inviterUsername) {
        FirebaseRepository.getInstance().getFriends(inviterUsername, friends -> {
            if (friends == null || friends.isEmpty()) {
                requireActivity().runOnUiThread(() -> 
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Inviter des amis")
                        .setMessage("Vous n'avez pas encore d'amis (suivis mutuels). Suivez des utilisateurs et attendez qu'ils vous suivent en retour pour les inviter.")
                        .setPositiveButton("Fermer", null)
                        .show());
                return;
            }

            String[] friendArray = friends.toArray(new String[0]);
            boolean[] checkedItems = new boolean[friendArray.length];

            requireActivity().runOnUiThread(() -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Inviter des amis dans " + groupName)
                        .setMultiChoiceItems(friendArray, checkedItems, (dialog, which, isChecked) -> {
                            checkedItems[which] = isChecked;
                        })
                        .setPositiveButton("Inviter", (dialog, which) -> {
                            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                            int count = 0;
                            for (int i = 0; i < friendArray.length; i++) {
                                if (checkedItems[i]) {
                                    FirebaseRepository.getInstance().sendGroupInvitation(
                                            groupName, groupId, inviterUsername, friendArray[i], date);
                                    count++;
                                }
                            }
                            if (count > 0) {
                                Toast.makeText(getContext(), count + " invitation(s) envoyée(s)", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatListener != null) { chatListener.remove(); chatListener = null; }
        if (membersListener != null) { membersListener.remove(); membersListener = null; }
    }

    private List<ChatItem> buildFeed(List<GroupMessage> messages, List<Photo> posts) {
        List<ChatItem> items = new ArrayList<>();
        for (GroupMessage m : messages) items.add(ChatItem.fromMessage(m));
        for (Photo p : posts) items.add(ChatItem.fromPost(p));
        Collections.sort(items);
        return items;
    }

    static class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.RVH> {
        private List<GroupMember> requests = new ArrayList<>();
        private final SharedViewModel viewModel;
        private final long groupId;
        private final String groupName;

        RequestAdapter(SharedViewModel vm, long gid, String gname) {
            this.viewModel  = vm;
            this.groupId    = gid;
            this.groupName  = gname;
        }

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
                FirebaseRepository.getInstance().saveGroupMember(groupName, m.userName, "MEMBER");
                AppNotification notif = new AppNotification();
                notif.targetUserId = m.userId;
                notif.type    = "JOIN_ACCEPTED";
                notif.message = "Votre demande d'adhésion a été acceptée !";
                notif.groupId = groupId;
                notif.date    = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                viewModel.insertAppNotification(notif);
                FirebaseRepository.getInstance().saveNotification(m.userName, notif);
                Toast.makeText(v.getContext(), name + " accepté !", Toast.LENGTH_SHORT).show();
            });
            h.btnReject.setOnClickListener(v -> {
                FirebaseRepository.getInstance().deleteGroupMember(groupName, m.userName);
                Toast.makeText(v.getContext(), "Demande refusée.", Toast.LENGTH_SHORT).show();
            });
        }

        @Override public int getItemCount() { return requests.size(); }

        void setRequests(List<GroupMember> list) {
            this.requests = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }

    static class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<ChatItem> items = new ArrayList<>();
        private final long currentUserId;
        private final String currentUsername;

        ChatAdapter(long uid, String username) {
            this.currentUserId   = uid;
            this.currentUsername = username != null ? username : "";
        }

        static class MessageVH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            android.widget.LinearLayout bubble;
            TextView tvAuthor, tvText, tvDate;
            MessageVH(View v) {
                super(v);
                card     = v.findViewById(R.id.msg_card);
                bubble   = v.findViewById(R.id.msg_bubble);
                tvAuthor = v.findViewById(R.id.tv_msg_author);
                tvText   = v.findViewById(R.id.tv_msg_text);
                tvDate   = v.findViewById(R.id.tv_msg_date);
            }
        }

        static class SharedPhotoVH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            ImageView ivImage;
            TextView tvTitle, tvLocation, tvAuthor;
            SharedPhotoVH(View v) {
                super(v);
                card       = v.findViewById(R.id.card_shared);
                ivImage    = v.findViewById(R.id.iv_shared_image);
                tvTitle    = v.findViewById(R.id.tv_shared_title);
                tvLocation = v.findViewById(R.id.tv_shared_location);
                tvAuthor   = v.findViewById(R.id.tv_shared_author);
            }
        }

        static class SharedPlanVH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView tvTitle, tvAuthor;
            SharedPlanVH(View v) {
                super(v);
                card     = v.findViewById(R.id.card_shared);
                tvTitle  = v.findViewById(R.id.tv_shared_title);
                tvAuthor = v.findViewById(R.id.tv_shared_author);
                // We'll reuse item_shared_photo layout but hide/adjust fields if needed
            }
        }

        static class PostVH extends RecyclerView.ViewHolder {
            android.widget.FrameLayout cardContainer;
            com.google.android.material.card.MaterialCardView card;
            ImageView ivImage;
            TextView tvTitle, tvMeta, tvAuthor;
            PostVH(View v) {
                super(v);
                cardContainer = (android.widget.FrameLayout) v;
                card     = v.findViewById(R.id.card_chat_post);
                ivImage  = v.findViewById(R.id.iv_chat_post_image);
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
            if (viewType == ChatItem.TYPE_SHARED) {
                return new SharedPhotoVH(inf.inflate(R.layout.item_shared_photo, parent, false));
            }
            if (viewType == ChatItem.TYPE_PLAN) {
                return new SharedPlanVH(inf.inflate(R.layout.item_shared_photo, parent, false));
            }
            return new MessageVH(inf.inflate(R.layout.item_group_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatItem item = items.get(position);
            if (item.type == ChatItem.TYPE_MESSAGE) {
                bindMessage((MessageVH) holder, item.message);
            } else if (item.type == ChatItem.TYPE_SHARED) {
                bindSharedPhoto((SharedPhotoVH) holder, item.message);
            } else if (item.type == ChatItem.TYPE_PLAN) {
                bindSharedPlan((SharedPlanVH) holder, item.message);
            } else {
                bindPost((PostVH) holder, item.post);
            }
        }

        private void bindMessage(MessageVH h, GroupMessage m) {
            boolean mine = !currentUsername.isEmpty()
                    && currentUsername.equalsIgnoreCase(m.authorName);

            android.widget.LinearLayout.LayoutParams lp =
                    (android.widget.LinearLayout.LayoutParams) h.card.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.card.setLayoutParams(lp);

            int bgColor = mine
                    ? h.bubble.getContext().getResources().getColor(R.color.coral, null)
                    : h.bubble.getContext().getResources().getColor(R.color.surface_field, null);
            h.card.setCardBackgroundColor(bgColor);

            h.tvAuthor.setTextColor(mine
                    ? h.tvAuthor.getContext().getResources().getColor(R.color.default_white, null)
                    : h.tvAuthor.getContext().getResources().getColor(R.color.sand, null));
            h.tvAuthor.setText(mine ? "Moi" : m.authorName);
            h.tvAuthor.setVisibility(mine ? View.GONE : View.VISIBLE);
            
            h.tvAuthor.setOnClickListener(v -> {
                if (!mine && m.authorName != null) {
                    android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.travelshare.ui.UserProfileActivity.class);
                    intent.putExtra(com.example.travelshare.ui.UserProfileActivity.EXTRA_USERNAME, m.authorName);
                    v.getContext().startActivity(intent);
                }
            });

            h.tvText.setTextColor(h.tvText.getContext().getResources().getColor(R.color.default_white, null));
            h.tvText.setText(m.message);
            h.tvDate.setText(m.date);
            h.tvDate.setTextColor(mine
                    ? h.tvDate.getContext().getResources().getColor(R.color.default_white, null)
                    : h.tvDate.getContext().getResources().getColor(R.color.ink_faint, null));
        }

        private void bindSharedPlan(SharedPlanVH h, GroupMessage m) {
            boolean mine = !currentUsername.isEmpty()
                    && currentUsername.equalsIgnoreCase(m.authorName);

            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) h.card.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.card.setLayoutParams(lp);

            h.tvTitle.setText(m.message); // Contains "🗺️ Parcours à..."
            h.tvAuthor.setText(mine ? "Moi" : m.authorName);
            
            // Hide specific photo fields if they exist in item_shared_photo
            View iv = h.itemView.findViewById(R.id.iv_shared_image);
            if (iv != null) iv.setVisibility(View.GONE);
            View loc = h.itemView.findViewById(R.id.tv_shared_location);
            if (loc != null) loc.setVisibility(View.GONE);

            h.itemView.setOnClickListener(v -> {
                if (m.planId > 0) {
                    androidx.fragment.app.FragmentActivity act = (androidx.fragment.app.FragmentActivity) v.getContext();
                    act.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, PlanDetailFragment.newInstance(m.planId))
                            .addToBackStack(null)
                            .commit();
                }
            });
        }

        private void bindSharedPhoto(SharedPhotoVH h, GroupMessage m) {
            boolean mine = !currentUsername.isEmpty()
                    && currentUsername.equalsIgnoreCase(m.authorName);
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) h.card.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.card.setLayoutParams(lp);
            h.tvAuthor.setText(mine ? "Moi" : "par @" + m.authorName);

            String msg = m.message != null ? m.message : "";
            String displayTitle = msg.replace("📸 ", "").replaceAll(" — 📍.*", "");
            String displayLoc   = msg.contains("📍 ") ? "📍 " + msg.substring(msg.indexOf("📍 ") + 3) : "";
            h.tvTitle.setText(displayTitle);
            h.tvLocation.setText(displayLoc);

            com.example.travelshare.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                com.example.travelshare.data.models.Photo photo =
                        com.example.travelshare.data.AppDatabase
                                .getInstance(h.itemView.getContext())
                                .photoDao().getPhotoById(m.photoId);
                h.itemView.post(() -> {
                    if (photo == null) return;
                    String uri = photo.getImageUri();
                    if (uri != null && !uri.isEmpty()) {
                        h.ivImage.setVisibility(View.VISIBLE);
                        Glide.with(h.ivImage.getContext()).load(android.net.Uri.parse(uri)).centerCrop().into(h.ivImage);
                    }
                    h.itemView.setOnClickListener(v -> {
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
                        intent.putExtra(PhotoDetailActivity.EXTRA_VOICE_URI, photo.getVoiceUri());
                        v.getContext().startActivity(intent);
                    });
                });
            });
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
                Glide.with(h.ivImage.getContext()).load(Uri.parse(uri)).centerCrop().into(h.ivImage);
            } else {
                h.ivImage.setVisibility(View.GONE);
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
                intent.putExtra(PhotoDetailActivity.EXTRA_VOICE_URI, p.getVoiceUri());
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
