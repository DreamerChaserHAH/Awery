package com.mrboomdev.awery.data.settings;

import com.mrboomdev.awery.util.exceptions.UnimplementedException;

public abstract class CustomSettingsItem extends SettingsItem {

	public CustomSettingsItem() {}

	public CustomSettingsItem(SettingsItemType type) {
		this.type = type;
	}

	public void saveValue(Object value) {}

	public Object getSavedValue() {
		return switch(getType()) {
			case BOOLEAN -> getBooleanValue();
			case STRING, SELECT -> getStringValue();
			case MULTISELECT -> getStringSetValue();
			case INTEGER, SELECT_INTEGER -> getIntegerValue();
			default -> throw new UnimplementedException("Unsupported type: " + getType());
		};
	}
}