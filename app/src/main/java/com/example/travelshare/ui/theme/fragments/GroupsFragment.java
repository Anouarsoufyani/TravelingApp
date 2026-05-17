package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupsFragment extends Fragment {

    private static final int TAB_MY   = 0;
    private static final int TAB_DISC = 1;
    private int currentTab = TAB_MY;

    private SharedViewModel viewModel;
    private SessionManager session;
    private GroupAdapter adapter;
    private ListenerRegistration groupsListener;
    private ListenerRegistration membershipsListener;
    private ListenerRegistration directChatsListener;
    
    private final List<Group> firebaseGroups = new ArrayList<>();
    private final List<Conversation> directConversations = new ArrayList<>();
    private final Map<String, String> membershipsByName = new HashMap<>();

    public static class Conversation {
        public boolean isGroup;
        public long id;
        public String name;
        public String description;
        public String creatorUsername;
        public String targetUsername; // For direct chats
        
        public static Conversation fromGroup(Group g) {
            Conversation c = new Conversation();
            c.isGroup = true;
            c.id = g.id;
            c.name = g.name;
            c.description = g.description;
            c.creatorUsername = g.creatorUsername;
            return c;
        }

        public static Conversation fromDirectChat(Map<String, Object> map, String currentUsername) {
            Conversation c = new Conversation();
            c.isGroup = false;
            c.description = (String) map.get("lastMessage");
            List<String> participants = (List<String>) map.get("participants");
            if (participants != null) {
                for (String p : participants) {
                    if (!p.equalsIgnoreCase(currentUsername)) {
                        c.name = p;
                        c.targetUsername = p;
                        break;
                    }
                }
            }
            if (c.name == null) c.name = "Inconnu";
            return c;
        }
    }

    private View layoutSearch;
    private EditText etSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        session   = new SessionManager(requireContext());

        com.google.android.material.tabs.TabLayout tabs = view.findViewById(R.id.tabs_groups);
        layoutSearch = view.findViewById(R.id.layout_search);
        etSearch     = view.findViewById(R.id.et_search_groups);

        RecyclerView rv = view.findViewById(R.id.rv_groups);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GroupAdapter(viewModel, session, this);
        rv.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderGroups();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.btn_create_group).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour créer un groupe.", Toast.LENGTH_SHORT).show();
                return;
            }
            showCreateDialog();
        });

        startFirestoreListeners();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        session = new SessionManager(requireContext());
        adapter.setSession(session);
        restartMembershipListener();
        startDirectChatsListener();
        renderGroups();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (groupsListener != null) { groupsListener.remove(); groupsListener = null; }
        if (membershipsListener != null) { membershipsListener.remove(); membershipsListener = null; }
        if (directChatsListener != null) { directChatsListener.remove(); directChatsListener = null; }
    }

    private void startFirestoreListeners() {
        if (groupsListener != null) groupsListener.remove();
        groupsListener = FirebaseRepository.getInstance().listenToGroups(groups -> {
            firebaseGroups.clear();
            if (groups != null) firebaseGroups.addAll(groups);
            if (isAdded()) requireActivity().runOnUiThread(this::renderGroups);
        });
        restartMembershipListener();
        startDirectChatsListener();
    }

    private void startDirectChatsListener() {
        if (directChatsListener != null) directChatsListener.remove();
        if (!session.isLoggedIn()) return;
        
        directChatsListener = FirebaseRepository.getInstance().listenToMyDirectChats(session.getUsername(), chats -> {
            directConversations.clear();
            if (chats != null) {
                for (Map<String, Object> m : chats) {
                    directConversations.add(Conversation.fromDirectChat(m, session.getUsername()));
                }
            }
            if (isAdded()) requireActivity().runOnUiThread(this::renderGroups);
        });
    }

    private void restartMembershipListener() {
        if (membershipsListener != null) {
            membershipsListener.remove();
            membershipsListener = null;
        }
        membershipsByName.clear();
        if (!session.isLoggedIn()) {
            if (adapter != null) adapter.setMemberships(membershipsByName);
            return;
        }
        membershipsListener = FirebaseRepository.getInstance()
                .listenToMyGroupMemberships(session.getUsername(), statuses -> {
                    membershipsByName.clear();
                    if (statuses != null) membershipsByName.putAll(statuses);
                    if (isAdded()) requireActivity().runOnUiThread(this::renderGroups);
                });
    }

    private void switchTab(int tab) {
        currentTab = tab;
        boolean myTab = (tab == TAB_MY);

        layoutSearch.setVisibility(myTab ? View.GONE : View.VISIBLE);
        if (myTab) etSearch.setText("");

        adapter.setMode(myTab ? GroupAdapter.MODE_MY : GroupAdapter.MODE_DISCOVER);
        renderGroups();
    }

    private void renderGroups() {
        if (adapter == null) return;
        adapter.setMemberships(membershipsByName);
        
        String currentUsername = session.getUsername();
        String query = etSearch != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        
        if (currentTab == TAB_MY) {
            // My Conversations (Direct + Groups I'm in + All Friends)
            if (!session.isLoggedIn()) {
                adapter.setConversations(new ArrayList<>());
                return;
            }

            FirebaseRepository.getInstance().getFriends(currentUsername, friends -> {
                List<Conversation> visible = new ArrayList<>();
                
                // 1. All Friends (as potential direct chats)
                if (friends != null) {
                    for (String friend : friends) {
                        Conversation c = new Conversation();
                        c.isGroup = false;
                        c.name = friend;
                        c.targetUsername = friend;
                        c.description = "Ami"; // Default status
                        
                        // If we already have a real direct chat with messages, use that instead
                        for (Conversation existing : directConversations) {
                            if (friend.equalsIgnoreCase(existing.targetUsername)) {
                                c.description = existing.description;
                                break;
                            }
                        }
                        visible.add(c);
                    }
                }

                // 2. Groups
                for (Group g : firebaseGroups) {
                    boolean isCreator = isCreator(g, currentUsername);
                    String status = membershipsByName.get(g.name);
                    boolean isMember = "MEMBER".equals(status);
                    if (isCreator || isMember) {
                        visible.add(Conversation.fromGroup(g));
                    }
                }

                if (isAdded()) requireActivity().runOnUiThread(() -> adapter.setConversations(visible));
            });
        } else {
            // Groups to Discover (Public groups)
            List<Conversation> discoverList = new ArrayList<>();
            for (Group g : firebaseGroups) {
                if (!query.isEmpty()) {
                    String haystack = ((g.name != null ? g.name : "") + " "
                            + (g.description != null ? g.description : "")).toLowerCase(Locale.ROOT);
                    if (!haystack.contains(query)) continue;
                }
                discoverList.add(Conversation.fromGroup(g));
            }
            adapter.setConversations(discoverList);
        }
    }

    private static boolean isCreator(Group g, String username) {
        return g.creatorUsername != null
                && username != null
                && g.creatorUsername.equalsIgnoreCase(username);
    }

    private void showCreateDialog() {
        EditText etName = new EditText(getContext());
        etName.setHint("Nom du groupe");
        EditText etDesc = new EditText(getContext());
        etDesc.setHint("Description (optionnel)");
        RadioGroup rgVisibility = new RadioGroup(getContext());
        rgVisibility.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbPrivate = new RadioButton(getContext());
        rbPrivate.setText("Privé");
        rbPrivate.setId(View.generateViewId());
        RadioButton rbPublic = new RadioButton(getContext());
        rbPublic.setText("Public");
        rbPublic.setId(View.generateViewId());
        rgVisibility.addView(rbPrivate);
        rgVisibility.addView(rbPublic);
        rgVisibility.check(rbPrivate.getId());
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);
        layout.addView(etName);
        layout.addView(etDesc);
        layout.addView(rgVisibility);

        new AlertDialog.Builder(requireContext())
                .setTitle("Créer un groupe")
                .setView(layout)
                .setPositiveButton("Créer", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Group g = new Group();
                    g.name = name;
                    g.description = etDesc.getText().toString().trim();
                    g.creatorId = FirebaseRepository.getInstance().stableUserId(session.getUsername());
                    g.creatorUsername = session.getUsername();
                    g.visibility = rgVisibility.getCheckedRadioButtonId() == rbPublic.getId()
                            ? Group.VISIBILITY_PUBLIC : Group.VISIBILITY_PRIVATE;
                    FirebaseRepository.getInstance().saveGroup(
                            name, g.description, session.getUsername(), g.creatorId, g.visibility);
                    Toast.makeText(getContext(),
                            "Groupe " + ("PUBLIC".equals(g.visibility) ? "public" : "privé")
                                    + " \"" + name + "\" créé !",
                            Toast.LENGTH_SHORT).show();
                    switchTab(TAB_MY);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GVH> {

        static final int MODE_MY       = 0;
        static final int MODE_DISCOVER = 1;

        private List<Conversation> conversations = new ArrayList<>();
        private int mode = MODE_MY;
        private SessionManager session;
        private final SharedViewModel viewModel;
        private final Fragment fragment;

        private final Map<String, String> memberships = new HashMap<>();

        GroupAdapter(SharedViewModel vm, SessionManager s, Fragment f) {
            this.viewModel = vm;
            this.session   = s;
            this.fragment  = f;
        }

        void setSession(SessionManager s) { this.session = s; }
        void setMode(int m)               { mode = m; notifyDataSetChanged(); }

        void setMemberships(Map<String, String> statuses) {
            memberships.clear();
            if (statuses != null) memberships.putAll(statuses);
            notifyDataSetChanged();
        }

        void setConversations(List<Conversation> list) {
            this.conversations = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        static class GVH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvName, tvDesc, tvAction, tvChat, tvUnreadBadge;
            GVH(View v) {
                super(v);
                tvAvatar      = v.findViewById(R.id.tv_group_avatar);
                tvName        = v.findViewById(R.id.tv_group_name);
                tvDesc        = v.findViewById(R.id.tv_group_desc);
                tvAction      = v.findViewById(R.id.tv_group_action);
                tvChat        = v.findViewById(R.id.tv_group_chat);
                tvUnreadBadge = v.findViewById(R.id.tv_unread_badge);
            }
        }

        @NonNull @Override
        public GVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull GVH h, int position) {
            Conversation c = conversations.get(position);
            
            String initial = (c.name != null && !c.name.isEmpty())
                    ? String.valueOf(c.name.charAt(0)).toUpperCase() : "?";
            
            h.tvAvatar.setText(c.isGroup ? "👥" : initial);
            h.tvAvatar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    h.itemView.getContext().getResources().getColor(c.isGroup ? R.color.surface_field : R.color.accent_soft, null)));
            
            h.tvName.setText(c.name);
            
            if (mode == MODE_MY) {
                bindMyConversation(h, c);
            } else {
                // In Discover mode, we only show groups
                bindDiscoverGroup(h, c);
            }
        }

        private void bindMyConversation(GVH h, Conversation c) {
            if (c.isGroup) {
                boolean isCreator = session.isLoggedIn() && c.creatorUsername != null && c.creatorUsername.equalsIgnoreCase(session.getUsername());
                h.tvAction.setVisibility(isCreator ? View.VISIBLE : View.GONE);
                if (isCreator) {
                    h.tvAction.setText("👑 Admin");
                    h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.sand, null));
                }
                h.tvDesc.setText("Groupe · " + (c.description != null ? c.description : ""));
                h.itemView.setOnClickListener(v -> openGroupChat(v, c));
            } else {
                h.tvAction.setVisibility(View.VISIBLE);
                h.tvAction.setText("Privé");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.sheet_muted, null));
                h.tvDesc.setText(c.description != null ? c.description : "Démarrer la discussion");
                h.itemView.setOnClickListener(v -> openDirectChat(v, c.targetUsername));
            }
            h.tvUnreadBadge.setVisibility(View.GONE); // Simplified for now
        }

        private void bindDiscoverGroup(GVH h, Conversation c) {
            h.tvAction.setVisibility(View.VISIBLE);
            h.tvDesc.setText("Groupe public · " + (c.description != null ? c.description : ""));
            
            boolean isCreator = session.isLoggedIn() && c.creatorUsername != null && c.creatorUsername.equalsIgnoreCase(session.getUsername());
            if (isCreator) {
                h.tvAction.setText("👑 Mon groupe");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.sand, null));
                h.itemView.setOnClickListener(v -> openGroupChat(v, c));
                return;
            }

            String status = memberships.get(c.name);
            if ("MEMBER".equals(status)) {
                h.tvAction.setText("Membre ✓");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.comfort_green, null));
                h.itemView.setOnClickListener(v -> openGroupChat(v, c));
            } else {
                h.itemView.setOnClickListener(null);
                h.tvAction.setText("Rejoindre →");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.coral, null));
                h.tvAction.setOnClickListener(v -> {
                    FirebaseRepository.getInstance().saveGroupMember(c.name, session.getUsername(), "MEMBER");
                    memberships.put(c.name, "MEMBER");
                    notifyDataSetChanged();
                });
            }
        }

        private void openGroupChat(View v, Conversation c) {
            androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) v.getContext();
            // Look up the actual group ID from firebaseGroups if missing
            long realId = c.id;
            if (realId <= 0 && fragment instanceof GroupsFragment) {
                for (Group g : ((GroupsFragment)fragment).firebaseGroups) {
                    if (g.name.equals(c.name)) { realId = g.id; break; }
                }
            }
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, GroupChatFragment.newInstance(realId, c.name, -1, c.creatorUsername))
                    .addToBackStack(null).commit();
        }

        private void openDirectChat(View v, String target) {
            androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) v.getContext();
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, DirectChatFragment.newInstance(target))
                    .addToBackStack(null).commit();
        }

        @Override public int getItemCount() { return conversations.size(); }
    }
}
