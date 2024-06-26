package com.mrboomdev.awery.extensions.support.yomi.tachiyomi;

import static com.mrboomdev.awery.extensions.ExtensionProvider.FEATURE_MEDIA_READ;
import static com.mrboomdev.awery.extensions.ExtensionProvider.FEATURE_MEDIA_SEARCH;
import static com.mrboomdev.awery.util.NiceUtils.stream;

import com.mrboomdev.awery.extensions.Extension;
import com.mrboomdev.awery.extensions.ExtensionProvider;
import com.mrboomdev.awery.extensions.support.yomi.YomiManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import eu.kanade.tachiyomi.source.CatalogueSource;
import eu.kanade.tachiyomi.source.SourceFactory;
import java9.util.stream.StreamSupport;

public class TachiyomiManager extends YomiManager {
	private final List<Integer> BASE_FEATURES = List.of(FEATURE_MEDIA_READ, FEATURE_MEDIA_SEARCH);

	@Override
	public String getName() {
		return "Tachiyomi";
	}

	@Override
	public String getId() {
		return "TACHIYOMI_KOTLIN";
	}

	@Override
	public String getMainClassMeta() {
		return "tachiyomi.extension.class";
	}

	@Override
	public String getNsfwMeta() {
		return "tachiyomi.extension.nsfw";
	}

	@Override
	public String getRequiredFeature() {
		return "tachiyomi.extension";
	}

	@Override
	public String getPrefix() {
		return "Tachiyomi: ";
	}

	@Override
	public double getMinVersion() {
		return 1.2;
	}

	@Override
	public double getMaxVersion() {
		return 1.5;
	}

	@Override
	public Collection<Integer> getBaseFeatures() {
		return BASE_FEATURES;
	}

	@Override
	public List<ExtensionProvider> createProviders(Extension extension, Object main) {
		if(main instanceof CatalogueSource source) {
			return List.of(new TachiyomiProvider(this, extension, source));
		} else if(main instanceof SourceFactory factory) {
			return stream(factory.createSources())
					.map(source -> createProviders(extension, source))
					.flatMap(StreamSupport::stream).toList();
		}

		return Collections.emptyList();
	}
}