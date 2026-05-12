package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.travelshare.R;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.viewmodels.SharedViewModel;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

public class MapFragment extends Fragment {

    private MapView mapView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Vue initiale centrée sur la France
        mapView.getController().setZoom(5.5);
        mapView.getController().setCenter(new GeoPoint(46.5, 2.5));

        TextView tvCount = view.findViewById(R.id.tv_map_count);

        // Observer les photos et placer les markers
        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        viewModel.getAllPhotos().observe(getViewLifecycleOwner(), photos -> {
            addMarkers(photos);
            if (photos != null) {
                tvCount.setText(photos.size() + " photo" + (photos.size() > 1 ? "s" : ""));
            }
        });


        return view;
    }

    private void addMarkers(List<Photo> photos) {
        mapView.getOverlays().clear();
        if (photos == null || photos.isEmpty()) return;

        for (Photo photo : photos) {
            double lat = photo.getLatitude();
            double lng = photo.getLongitude();

            // Ignorer les photos sans coordonnées
            if (lat == 0.0 && lng == 0.0) continue;

            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(lat, lng));
            marker.setTitle(photo.getTitle());
            marker.setSnippet("📍 " + photo.getLocation() + "  •  " + photo.getAuthor());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // Clic direct sur le marker → ouvrir la fiche détaillée
            marker.setOnMarkerClickListener((m, mv) -> {
                Intent intent = new Intent(requireContext(), PhotoDetailActivity.class);
                intent.putExtra(PhotoDetailActivity.EXTRA_PHOTO_ID, photo.getId());
                intent.putExtra(PhotoDetailActivity.EXTRA_TITLE, photo.getTitle());
                intent.putExtra(PhotoDetailActivity.EXTRA_AUTHOR, photo.getAuthor());
                intent.putExtra(PhotoDetailActivity.EXTRA_DATE, photo.getDate());
                intent.putExtra(PhotoDetailActivity.EXTRA_LOCATION, photo.getLocation());
                intent.putExtra(PhotoDetailActivity.EXTRA_CATEGORY, photo.getCategory());
                intent.putExtra(PhotoDetailActivity.EXTRA_TAGS, photo.getTags());
                intent.putExtra(PhotoDetailActivity.EXTRA_LIKES, photo.getLikes());
                intent.putExtra(PhotoDetailActivity.EXTRA_LAT, lat);
                intent.putExtra(PhotoDetailActivity.EXTRA_LNG, lng);
                intent.putExtra(PhotoDetailActivity.EXTRA_IMAGE_URI, photo.getImageUri());
                startActivity(intent);
                return true;
            });

            mapView.getOverlays().add(marker);
        }

        mapView.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
