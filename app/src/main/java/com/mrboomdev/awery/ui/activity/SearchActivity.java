package com.mrboomdev.awery.ui.activity;

import static com.mrboomdev.awery.app.AweryApp.enableEdgeToEdge;
import static com.mrboomdev.awery.app.AweryApp.isLandscape;
import static com.mrboomdev.awery.app.AweryLifecycle.runDelayed;
import static com.mrboomdev.awery.util.NiceUtils.doIfNotNull;
import static com.mrboomdev.awery.util.NiceUtils.returnWith;
import static com.mrboomdev.awery.util.ui.ViewUtil.createLinearParams;
import static com.mrboomdev.awery.util.ui.ViewUtil.dpPx;
import static com.mrboomdev.awery.util.ui.ViewUtil.setHorizontalMargin;
import static com.mrboomdev.awery.util.ui.ViewUtil.setHorizontalPadding;
import static com.mrboomdev.awery.util.ui.ViewUtil.setOnApplyUiInsetsListener;
import static com.mrboomdev.awery.util.ui.ViewUtil.setTopMargin;
import static com.mrboomdev.awery.util.ui.ViewUtil.setVerticalPadding;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.mrboomdev.awery.R;
import com.mrboomdev.awery.app.AweryLifecycle;
import com.mrboomdev.awery.data.settings.SettingsItem;
import com.mrboomdev.awery.data.settings.SettingsItemType;
import com.mrboomdev.awery.databinding.GridMediaCatalogBinding;
import com.mrboomdev.awery.databinding.LayoutHeaderSearchBinding;
import com.mrboomdev.awery.databinding.LayoutLoadingBinding;
import com.mrboomdev.awery.extensions.Extension;
import com.mrboomdev.awery.extensions.ExtensionProvider;
import com.mrboomdev.awery.extensions.ExtensionsFactory;
import com.mrboomdev.awery.extensions.data.CatalogMedia;
import com.mrboomdev.awery.extensions.data.CatalogSearchResults;
import com.mrboomdev.awery.extensions.support.anilist.AnilistProvider;
import com.mrboomdev.awery.generated.AwerySettings;
import com.mrboomdev.awery.sdk.util.UniqueIdGenerator;
import com.mrboomdev.awery.ui.ThemeManager;
import com.mrboomdev.awery.ui.sheet.FiltersSheet;
import com.mrboomdev.awery.util.MediaUtils;
import com.mrboomdev.awery.util.Parser;
import com.mrboomdev.awery.util.exceptions.ExceptionDescriptor;
import com.mrboomdev.awery.util.exceptions.ZeroResultsException;
import com.mrboomdev.awery.util.ui.ViewUtil;
import com.mrboomdev.awery.util.ui.adapter.SingleViewAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchActivity extends AppCompatActivity {
	private static final int LOADING_VIEW_TYPE = 1;
	private static final String TAG = "SearchActivity";
	private final WeakHashMap<CatalogMedia, Long> ids = new WeakHashMap<>();
	private final Adapter adapter = new Adapter();
	private final UniqueIdGenerator idGenerator = new UniqueIdGenerator();
	private final List<CatalogMedia> items = new ArrayList<>();
	private List<SettingsItem> filters;
	private SingleViewAdapter.BindingSingleViewAdapter<LayoutLoadingBinding> loadingAdapter;
	private ExtensionProvider source;
	private LayoutHeaderSearchBinding header;
	private SwipeRefreshLayout refresher;
	private RecyclerView recycler;
	private boolean didReachedEnd, select;
	private int searchId, currentPage;

	private final SettingsItem queryFilter = new SettingsItem(
			SettingsItemType.STRING, ExtensionProvider.FILTER_QUERY);

	private final SettingsItem pageFilter = new SettingsItem(
			SettingsItemType.INTEGER, ExtensionProvider.FILTER_PAGE);

	/** We initially set this value to "true" so that list won't try
	 to load anything because we haven't typed anything yet. **/
	private boolean isLoading = true;

	@SuppressLint("NotifyDataSetChanged")
	@Override
	@SuppressWarnings("unchecked")
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		ThemeManager.apply(this);
		enableEdgeToEdge(this);
		super.onCreate(savedInstanceState);

		var columnsCountLand = new AtomicInteger(AwerySettings.MEDIA_COLUMNS_COUNT_LAND.getValue());
		var autoColumnsCountLand = columnsCountLand.get() == 0;

		var columnsCountPort = new AtomicInteger(AwerySettings.MEDIA_COLUMNS_COUNT_PORT.getValue());
		var autoColumnsCountPort = columnsCountPort.get() == 0;

		this.select = getIntent().getBooleanExtra("select", false);
		this.queryFilter.setValue(getIntent().getStringExtra("query"));

		this.filters = (List<SettingsItem>) getIntent().getSerializableExtra("filters");
		this.filters = new ArrayList<>(filters != null ? filters : Collections.emptyList());
		this.filters.addAll(List.of(queryFilter, pageFilter));

		this.source = returnWith(getIntent().getStringExtra("source"), sourceId -> {
			if(sourceId == null) {
				return AnilistProvider.getInstance();
			}

			return ExtensionsFactory.getExtensionProvider(Extension.FLAG_WORKING, sourceId);
		});

		var linear = new LinearLayoutCompat(this);
		linear.setOrientation(LinearLayoutCompat.VERTICAL);
		setContentView(linear);

		header = LayoutHeaderSearchBinding.inflate(getLayoutInflater(), linear, true);
		header.edittext.setText(queryFilter.getStringValue());
		header.edittext.requestFocus();

		header.back.setOnClickListener(v -> finish());
		header.clear.setOnClickListener(v -> header.edittext.setText(null));

		header.filters.setOnClickListener(v -> new FiltersSheet(this, filters, source, () -> {
			didReachedEnd = false;
			search(0);
		}).show());

		var inputManager = getSystemService(InputMethodManager.class);
		inputManager.showSoftInput(header.edittext, 0);

		header.edittext.setOnEditorActionListener((v, action, event) -> {
			if(action == EditorInfo.IME_ACTION_SEARCH) {
				inputManager.hideSoftInputFromWindow(
						header.edittext.getWindowToken(), 0);

				queryFilter.setValue(v.getText().toString());
				didReachedEnd = false;

				search(0);
				return true;
			}

			return false;
		});

		setOnApplyUiInsetsListener(header.getRoot(), insets -> {
			setTopMargin(header.getRoot(), insets.top);
			setHorizontalMargin(header.getRoot(), insets.left, insets.right);
			return true;
		});

		refresher = new SwipeRefreshLayout(this);
		linear.addView(refresher, createLinearParams(ViewUtil.MATCH_PARENT, ViewUtil.MATCH_PARENT));

		refresher.setOnRefreshListener(() -> {
			this.didReachedEnd = false;
			search(0);
		});

		var refresherContent = new LinearLayoutCompat(this);
		refresherContent.setOrientation(LinearLayoutCompat.VERTICAL);
		refresher.addView(refresherContent);

		loadingAdapter = SingleViewAdapter.fromBindingDynamic(parent -> {
			var binding = LayoutLoadingBinding.inflate(getLayoutInflater(), parent, false);
			ViewUtil.useLayoutParams(binding.getRoot(), params -> params.width = ViewUtil.MATCH_PARENT);
			return binding;
		}, LOADING_VIEW_TYPE);

		loadingAdapter.setEnabled(false);

		var concatAdapter = new ConcatAdapter(new ConcatAdapter.Config.Builder()
				.setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
				.build(), adapter, loadingAdapter);

		var layoutManager = new GridLayoutManager(this, isLandscape()
				? (autoColumnsCountLand ? 3 : columnsCountLand.get())
				: (autoColumnsCountPort ? 5 : columnsCountPort.get()));

		recycler = new RecyclerView(this);
		recycler.setLayoutManager(layoutManager);
		recycler.setClipToPadding(false);
		recycler.setAdapter(concatAdapter);
		refresherContent.addView(recycler);

		recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				tryLoadMore();
			}
		});

		setOnApplyUiInsetsListener(recycler, insets -> {
			var padding = dpPx(8);
			setVerticalPadding(recycler, padding + padding * 2);
			setHorizontalPadding(recycler, insets.left + padding, insets.right + padding);

			if(isLandscape() && autoColumnsCountLand) {
				float columnSize = dpPx(110);
				float freeSpace = getResources().getDisplayMetrics().widthPixels - (padding * 2) - insets.left - insets.right;
				columnsCountLand.set((int)(freeSpace / columnSize));
				layoutManager.setSpanCount(columnsCountLand.get());
			} else if(!isLandscape() && autoColumnsCountPort) {
				float columnSize = dpPx(110);
				float freeSpace = getResources().getDisplayMetrics().widthPixels - (padding * 2) - insets.left - insets.right;
				columnsCountPort.set((int)(freeSpace / columnSize));
				layoutManager.setSpanCount(columnsCountPort.get());
			}

			return true;
		});

		layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return (position < items.size()) ? 1 : (isLandscape()
						? (columnsCountLand.get() == 0 ? layoutManager.getSpanCount() : columnsCountLand.get())
						: columnsCountPort.get() == 0 ? layoutManager.getSpanCount() : columnsCountPort.get());
			}
		});

		doIfNotNull((List<CatalogMedia>) getIntent().getSerializableExtra("loadedMedia"), loadedMedia -> {
			for(var item : loadedMedia) {
				ids.put(item, idGenerator.getLong());
			}

			isLoading = false;
			items.addAll(loadedMedia);
			adapter.notifyItemRangeInserted(0, items.size());
		});
	}

	private void tryLoadMore() {
		if(!isLoading && !didReachedEnd) {
			var lastIndex = items.size() - 1;

			if(recycler.getLayoutManager() instanceof LinearLayoutManager manager
					&& manager.findLastVisibleItemPosition() >= lastIndex) {
				search(currentPage + 1);
			}
		}
	}

	private void reachedEnd(long wasSearchId) {
		loadingAdapter.getBinding((binding) -> runOnUiThread(() -> {
			if(wasSearchId != searchId) return;

			SearchActivity.this.didReachedEnd = true;
			binding.title.setText(R.string.you_reached_end);
			binding.message.setText(R.string.you_reached_end_description);

			binding.progressBar.setVisibility(View.GONE);
			binding.info.setVisibility(View.VISIBLE);

			isLoading = false;
			didReachedEnd = true;
		}));
	}

	@SuppressLint("NotifyDataSetChanged")
	private void search(int page) {
		if(queryFilter.getStringValue() == null) {
			queryFilter.setValue("");
		}

		var wasSearchId = ++searchId;

		if(page == 0) {
			this.items.clear();
			adapter.notifyDataSetChanged();
			idGenerator.clear();
		}

		loadingAdapter.getBinding((binding) -> {
			binding.progressBar.setVisibility(View.VISIBLE);
			binding.info.setVisibility(View.GONE);
		});

		loadingAdapter.setEnabled(true);
		pageFilter.setValue(page);

		source.searchMedia(this, filters, new ExtensionProvider.ResponseCallback<>() {
			@SuppressLint("NotifyDataSetChanged")
			@Override
			public void onSuccess(CatalogSearchResults<? extends CatalogMedia> items) {
				if(wasSearchId != searchId) return;

				if(!items.hasNextPage()) {
					reachedEnd(wasSearchId);
				}

				MediaUtils.filterMedia(items, filteredItems -> {
					if(filteredItems.isEmpty()) {
						throw new ZeroResultsException("No media was found", R.string.no_media_found);
					}

					for(var item : filteredItems) {
						ids.put(item, idGenerator.getLong());
					}

					runOnUiThread(() -> {
						if(wasSearchId != searchId) return;
						SearchActivity.this.isLoading = false;
						SearchActivity.this.currentPage = page;

						if(page == 0) {
							SearchActivity.this.items.addAll(filteredItems);
							adapter.notifyDataSetChanged();
						} else {
							var wasSize = SearchActivity.this.items.size();
							SearchActivity.this.items.addAll(filteredItems);
							adapter.notifyItemRangeInserted(wasSize, filteredItems.size());
						}

						runDelayed(() -> tryLoadMore(), 1000, recycler);
					});
				});

				onFinally();
			}

			@SuppressLint("NotifyDataSetChanged")
			@Override
			public void onFailure(Throwable e) {
				if(wasSearchId != searchId) return;

				var error = new ExceptionDescriptor(e);
				Log.e(TAG, "Failed to search", e);

				runOnUiThread(() -> {
					if(wasSearchId != searchId) return;

					loadingAdapter.getBinding((binding) -> {
						if(wasSearchId != searchId) return;

						if(page == 0) {
							SearchActivity.this.items.clear();
							adapter.notifyDataSetChanged();
						}

						if(e instanceof ZeroResultsException && page != 0) {
							reachedEnd(wasSearchId);
						} else {
							binding.title.setText(error.getTitle(SearchActivity.this));
							binding.message.setText(error.getMessage(SearchActivity.this));
						}

						binding.progressBar.setVisibility(View.GONE);
						binding.info.setVisibility(View.VISIBLE);

						SearchActivity.this.isLoading = false;
					});
				});

				onFinally();
			}

			private void onFinally() {
				if(wasSearchId != searchId) return;
				refresher.setRefreshing(false);
			}
		});
	}

	private class Adapter extends RecyclerView.Adapter<ViewHolder> {

		public Adapter() {
			setHasStableIds(true);
		}

		@Override
		public long getItemId(int position) {
			return ids.get(items.get(position));
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			var inflater = LayoutInflater.from(parent.getContext());
			var binding = GridMediaCatalogBinding.inflate(inflater, parent, false);
			var viewHolder = new ViewHolder(binding);

			var params = binding.getRoot().getLayoutParams();
			params.width = ViewUtil.MATCH_PARENT;

			if(!setHorizontalMargin(params, dpPx(6))) {
				throw new IllegalStateException("Failed to set horizontal margin for GridMediaCatalogBinding!");
			}

			binding.getRoot().setLayoutParams(params);

			binding.getRoot().setOnClickListener(view -> {
				if(select) {
					var json = Parser.toString(CatalogMedia.class, viewHolder.getItem());
					setResult(0, new Intent().putExtra("media", json));
					finish();
					return;
				}

				MediaUtils.launchMediaActivity(parent.getContext(), viewHolder.getItem());
			});

			binding.getRoot().setOnLongClickListener(view -> {
				var media = viewHolder.getItem();
				var index = items.indexOf(media);

				MediaUtils.openMediaActionsMenu(parent.getContext(), media,
						() -> MediaUtils.isMediaFiltered(media, isFiltered -> {
					if(!isFiltered) return;

					AweryLifecycle.runOnUiThread(() -> {
						items.remove(media);
						notifyItemRemoved(index);
					});
				}));
				return true;
			});

			return viewHolder;
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			holder.bind(items.get(position));
		}

		@Override
		public int getItemCount() {
			return items.size();
		}
	}

	private static class ViewHolder extends RecyclerView.ViewHolder {
		private final GridMediaCatalogBinding binding;
		private CatalogMedia item;

		public ViewHolder(@NonNull GridMediaCatalogBinding binding) {
			super(binding.getRoot());
			this.binding = binding;
		}

		public CatalogMedia getItem() {
			return item;
		}

		public void bind(@NonNull CatalogMedia item) {
			this.item = item;

			binding.title.setText(item.getTitle());
			binding.ongoing.setVisibility(item.status == CatalogMedia.MediaStatus.ONGOING ? View.VISIBLE : View.GONE);

			if(item.averageScore != null) {
				binding.scoreWrapper.setVisibility(View.VISIBLE);
				binding.score.setText(String.valueOf(item.averageScore));
			} else {
				binding.scoreWrapper.setVisibility(View.GONE);
			}

			try {
				Glide.with(binding.getRoot())
						.load(item.poster.large)
						.transition(DrawableTransitionOptions.withCrossFade())
						.into(binding.mediaItemBanner);
			} catch(IllegalArgumentException e) {
				Log.e(TAG, "Failed to load a poster", e);
			}
		}
	}
}