package com.example.cref_wss_01;

import java.util.ArrayList;
import java.util.List;

public class CategoryItem extends NavItem {
    private final String name;
    private final List<SubCategoryItem> subCategories = new ArrayList<>();
    public boolean isExpanded = false;

    public CategoryItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<SubCategoryItem> getSubCategories() {
        return subCategories;
    }

    @Override
    public int getType() {
        return TYPE_CATEGORY;
    }
}
