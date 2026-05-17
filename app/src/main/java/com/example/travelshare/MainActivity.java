package com.example.travelshare;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.travelshare.ui.InscriptionFragment;
import com.example.travelshare.ui.theme.fragments.ExplorerFragment;
import com.example.travelshare.ui.theme.fragments.GroupsFragment;
import com.example.travelshare.ui.theme.fragments.TravelPathFragment;
import com.example.travelshare.ui.theme.fragments.MapFragment;
import com.example.travelshare.ui.theme.fragments.NotificationsFragment;
import com.example.travelshare.ui.theme.fragments.ProfileFragment;
import com.example.travelshare.ui.theme.fragments.PublishFragment;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements InscriptionFragment.OnInscriptionListener {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        com.example.travelshare.utils.SessionManager session = new com.example.travelshare.utils.SessionManager(this);
        if (!session.isLoggedIn()) {
            Intent intent = new Intent(this, com.example.travelshare.ui.LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);

        org.osmdroid.config.Configuration.getInstance().setUserAgentValue(getPackageName());
        org.osmdroid.config.Configuration.getInstance().setOsmdroidTileCache(
                new java.io.File(getCacheDir(), "osmdroid_tiles"));

        String openFragment = getIntent().getStringExtra("OPEN_FRAGMENT");
        boolean openTravelPath = getIntent().getBooleanExtra("OPEN_TRAVELPATH", false);
        String travelPathCity  = getIntent().getStringExtra("TRAVELPATH_CITY");

        if (savedInstanceState == null) {
            if ("INSCRIPTION".equals(openFragment)) {
                bottomNav.setVisibility(View.GONE);
                loadFragment(new InscriptionFragment());
            } else if (openTravelPath) {
                bottomNav.setVisibility(View.VISIBLE);
                bottomNav.setSelectedItemId(R.id.nav_profile);
                String city = travelPathCity != null ? travelPathCity : "";
                loadFragment(TravelPathFragment.newInstance(city));
            } else {
                bottomNav.setVisibility(View.VISIBLE);
                loadFragment(new ExplorerFragment());
            }
        }

        View btnBellContainer = findViewById(R.id.btn_notif_bell);

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_explorer) {
                selectedFragment = new ExplorerFragment();
            } else if (itemId == R.id.nav_map) {
                selectedFragment = new MapFragment();
            } else if (itemId == R.id.nav_publish) {
                selectedFragment = new PublishFragment();
            } else if (itemId == R.id.nav_groups) {
                selectedFragment = new GroupsFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            boolean hideBell = itemId == R.id.nav_groups || itemId == R.id.nav_map;
            btnBellContainer.setVisibility(hideBell ? View.GONE : View.VISIBLE);

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });

        View badge = findViewById(R.id.notif_badge);
        SharedViewModel viewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        long userId = new com.example.travelshare.utils.SessionManager(this).getUserId();
        viewModel.getUnreadNotificationCount(userId).observe(this, count ->
                badge.setVisibility(count != null && count > 0 ? View.VISIBLE : View.GONE));

        btnBellContainer.setOnClickListener(v -> loadFragment(new NotificationsFragment()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean openTravelPath = intent.getBooleanExtra("OPEN_TRAVELPATH", false);
        if (openTravelPath) {
            String city = intent.getStringExtra("TRAVELPATH_CITY");
            bottomNav.setVisibility(View.VISIBLE);
            loadFragment(TravelPathFragment.newInstance(city != null ? city : ""));
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public void onInscriptionReussie(long newUserId, String username) {
        new com.example.travelshare.utils.SessionManager(this)
                .createLoginSession((int) newUserId, username);
        Toast.makeText(this, "Bienvenue " + username + " !", Toast.LENGTH_SHORT).show();
        bottomNav.setVisibility(View.VISIBLE);
        loadFragment(new ExplorerFragment());
    }
}
