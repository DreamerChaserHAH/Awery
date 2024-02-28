package com.mrboomdev.awery.ui.fragments;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.chip.Chip;
import com.mrboomdev.awery.AweryApp;
import com.mrboomdev.awery.catalog.template.CatalogMedia;
import com.mrboomdev.awery.ui.activity.MediaActivity;
import com.mrboomdev.awery.util.TranslationUtil;
import com.mrboomdev.awery.util.ui.ViewUtil;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.Calendar;

import ani.awery.databinding.MediaDetailsOverviewLayoutBinding;

public class MediaInfoFragment extends Fragment {
	private MediaDetailsOverviewLayoutBinding binding;
	private CatalogMedia media;

	public MediaInfoFragment(CatalogMedia media) {
		setMedia(media);
	}

	public MediaInfoFragment() {
		this(null);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("media", media.toString());
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(savedInstanceState != null) {
			var moshi = new Moshi.Builder().build();
			var adapter = moshi.adapter(CatalogMedia.class);

			try {
				var mediaJson = savedInstanceState.getString("media");
				if(mediaJson == null) return;

				var media = adapter.fromJson(mediaJson);

				if(media != null) {
					setMedia(media);
				} else {
					throw new IOException("Failed to restore media!");
				}
			} catch(IOException e) {
				e.printStackTrace();
				AweryApp.toast("Failed to restore media!", 1);
			}
		}
	}

	@SuppressLint("SetTextI18n")
	public void setMedia(CatalogMedia media) {
		if(media == null) return;
		this.media = media;
		if(binding == null) return;

		Glide.with(binding.getRoot())
				.load(media.poster.extraLarge)
				.transition(DrawableTransitionOptions.withCrossFade())
				.into(binding.poster);

		var banner = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
				? media.getBestBanner() : media.getBestPoster();

		Glide.with(binding.getRoot())
				.load(banner)
				.transition(DrawableTransitionOptions.withCrossFade())
				.into(binding.banner);

		binding.details.play.setOnClickListener(v -> {
			if(requireActivity() instanceof MediaActivity activity) {
				activity.launchAction("watch");
			} else {
				throw new IllegalStateException("Activity is not an instance of MediaActivity!");
			}
		});

		binding.details.title.setText(media.title);
		binding.details.generalMeta.setText(generateGeneralMetaString(media));

		if(media.description != null) {
			binding.details.description.setText(Html.fromHtml(media.description, Html.FROM_HTML_MODE_COMPACT).toString().trim());
		} else {
			binding.details.description.setVisibility(View.GONE);
		}

		if(media.tags.isEmpty()) {
			binding.details.tagsTitle.setVisibility(View.GONE);
			binding.details.tags.setVisibility(View.GONE);
		} else {
			for(var tag : media.tags) {
				var chip = new Chip(requireContext());
				chip.setText(tag.name);
				binding.details.tags.addView(chip);
			}
		}
	}

	@NonNull
	private String generateGeneralMetaString(@NonNull CatalogMedia media) {
		var builder = new StringBuilder();

		if(media.episodesCount != null) {
			builder.append(media.episodesCount);

			if(media.episodesCount == 1) builder.append(" Episode");
			else builder.append(" Episodes");
		}

		if(media.duration != null) {
			if(builder.length() > 0) builder.append(" • ");

			if(media.duration < 60) {
				builder.append(media.duration).append(" min");
			} else {
				builder.append((media.duration / 60)).append(" h ").append((media.duration % 60)).append(" min");
			}

			builder.append(" Duration");
		}

		if(media.releaseDate != null) {
			if(builder.length() > 0) builder.append(" • ");
			builder.append(media.releaseDate.get(Calendar.YEAR));
		}

		if(media.country != null) {
			if(builder.length() > 0) builder.append(" • ");
			builder.append(TranslationUtil.getTranslatedLangName(requireContext(), media.country));
		}

		return builder.toString();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		binding = MediaDetailsOverviewLayoutBinding.inflate(inflater, container, false);

		if(binding.detailsScroller != null) ViewUtil.setOnApplyUiInsetsListener(binding.detailsScroller, insets -> {
			var margin = ViewUtil.dpPx(8);

			ViewUtil.setTopPadding(binding.detailsScroller, insets.top + margin);
			ViewUtil.setBottomPadding(binding.detailsScroller, insets.bottom + margin);
			ViewUtil.setRightPadding(binding.detailsScroller, insets.right + (margin * 2));
		});

		setMedia(media);
		return binding.getRoot();
	}
}