package com.example.cref_wss_01;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpandableNavAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<NavItem> items = new ArrayList<>();
    private OnQuestionClickListener listener;
    private Map<Integer, Answer> answerMap;
    private Set<Integer> questionsWithAttachments;

    public interface OnQuestionClickListener {
        void onQuestionClick(Question question);
    }

    public ExpandableNavAdapter(List<CategoryItem> categories, List<Answer> answers, List<MediaAttachment> attachments, OnQuestionClickListener listener) {
        this.listener = listener;
        this.answerMap = answers.stream().collect(Collectors.toMap(answer -> answer.questionId, answer -> answer, (a1, a2) -> a1));
        this.questionsWithAttachments = attachments.stream().map(attachment -> attachment.questionId).collect(Collectors.toSet());

        for (CategoryItem category : categories) {
            this.items.add(category);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case NavItem.TYPE_CATEGORY:
                return new CategoryViewHolder(inflater.inflate(R.layout.nav_item_category, parent, false));
            case NavItem.TYPE_SUB_CATEGORY:
                return new SubCategoryViewHolder(inflater.inflate(R.layout.nav_item_sub_category, parent, false));
            default: // TYPE_QUESTION
                return new QuestionViewHolder(inflater.inflate(R.layout.nav_item_question, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case NavItem.TYPE_CATEGORY:
                CategoryViewHolder categoryHolder = (CategoryViewHolder) holder;
                CategoryItem categoryItem = (CategoryItem) items.get(position);
                int categoryAnswered = countAnswered(categoryItem);
                int categoryTotal = countTotal(categoryItem);
                int categoryPercent = categoryTotal > 0 ? (int)(((double)categoryAnswered / categoryTotal) * 100) : 0;
                String categoryText = categoryItem.getName() + " (" + categoryItem.getSubCategories().size() + ") [" + categoryPercent + "%]";
                categoryHolder.textView.setText(categoryText);
                categoryHolder.expandIcon.setRotation(categoryItem.isExpanded ? 180 : 0);
                holder.itemView.setOnClickListener(v -> toggleCategory(categoryItem, holder.getAdapterPosition()));
                break;
            case NavItem.TYPE_SUB_CATEGORY:
                SubCategoryViewHolder subCategoryHolder = (SubCategoryViewHolder) holder;
                SubCategoryItem subCategoryItem = (SubCategoryItem) items.get(position);
                int subCategoryAnswered = countAnswered(subCategoryItem);
                int subCategoryTotal = subCategoryItem.getQuestions().size();
                int subCategoryPercent = subCategoryTotal > 0 ? (int)(((double)subCategoryAnswered / subCategoryTotal) * 100) : 0;
                String subCategoryText = subCategoryItem.getName() + " (" + subCategoryTotal + ") [" + subCategoryPercent + "%]";
                subCategoryHolder.textView.setText(subCategoryText);
                subCategoryHolder.expandIcon.setRotation(subCategoryItem.isExpanded ? 180 : 0);
                holder.itemView.setOnClickListener(v -> toggleSubCategory(subCategoryItem, holder.getAdapterPosition()));
                break;
            case NavItem.TYPE_QUESTION:
                QuestionViewHolder questionHolder = (QuestionViewHolder) holder;
                QuestionItem questionItem = (QuestionItem) items.get(position);
                questionHolder.questionText.setText(questionItem.getQuestion().getQuestionText());
                updateStatusIcons(questionHolder, questionItem.getQuestion());
                questionHolder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onQuestionClick(questionItem.getQuestion());
                    }
                });
                break;
        }
    }

    private void updateStatusIcons(QuestionViewHolder holder, Question question) {
        holder.statusIconContainer.removeAllViews();
        Answer answer = answerMap.get(question.getId());

        // **THE CRITICAL FIX for status icons**
        // Check if an answer exists AND its value is not empty
        if (answer != null && answer.answerValue != null && !answer.answerValue.isEmpty()) {
            ImageView icon = new ImageView(holder.itemView.getContext());
            icon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.ic_status_answered));
            holder.statusIconContainer.addView(icon);
        } else {
            ImageView icon = new ImageView(holder.itemView.getContext());
            icon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.ic_status_unanswered));
            holder.statusIconContainer.addView(icon);
        }

        // Separately, check if attachments exist for this question
        if (questionsWithAttachments.contains(question.getId())) {
            ImageView icon = new ImageView(holder.itemView.getContext());
            icon.setImageDrawable(ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.ic_attachment));
            holder.statusIconContainer.addView(icon);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void toggleCategory(CategoryItem categoryItem, int position) {
        boolean isExpanding = !categoryItem.isExpanded;
        categoryItem.isExpanded = isExpanding;
        notifyItemChanged(position);

        if (isExpanding) {
            items.addAll(position + 1, categoryItem.getSubCategories());
            notifyItemRangeInserted(position + 1, categoryItem.getSubCategories().size());
        } else {
            int itemsToRemove = 0;
            for (SubCategoryItem sub : categoryItem.getSubCategories()) {
                itemsToRemove++;
                if (sub.isExpanded) {
                    itemsToRemove += sub.getQuestions().size();
                    sub.isExpanded = false; // Force collapse children
                }
            }
            if (itemsToRemove > 0 && items.size() >= position + 1 + itemsToRemove) {
                items.subList(position + 1, position + 1 + itemsToRemove).clear();
                notifyItemRangeRemoved(position + 1, itemsToRemove);
            }
        }
    }

    private void toggleSubCategory(SubCategoryItem subCategoryItem, int position) {
        boolean isExpanding = !subCategoryItem.isExpanded;
        subCategoryItem.isExpanded = isExpanding;
        notifyItemChanged(position);

        if (isExpanding) {
            items.addAll(position + 1, subCategoryItem.getQuestions());
            notifyItemRangeInserted(position + 1, subCategoryItem.getQuestions().size());
        } else {
            if(items.size() >= position + 1 + subCategoryItem.getQuestions().size()){
                items.subList(position + 1, position + 1 + subCategoryItem.getQuestions().size()).clear();
                notifyItemRangeRemoved(position + 1, subCategoryItem.getQuestions().size());
            }
        }
    }

    private int countAnswered(CategoryItem category) {
        int count = 0;
        for (SubCategoryItem sub : category.getSubCategories()) {
            count += countAnswered(sub);
        }
        return count;
    }

    private int countAnswered(SubCategoryItem subCategory) {
        int count = 0;
        for (QuestionItem q : subCategory.getQuestions()) {
            Answer answer = answerMap.get(q.getQuestion().getId());
            if (answer != null && answer.answerValue != null && !answer.answerValue.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int countTotal(CategoryItem category) {
        int count = 0;
        for (SubCategoryItem sub : category.getSubCategories()) {
            count += sub.getQuestions().size();
        }
        return count;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ImageView expandIcon;
        CategoryViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.nav_item_category_name);
            expandIcon = itemView.findViewById(R.id.category_expand_icon);
        }
    }

    static class SubCategoryViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ImageView expandIcon;
        SubCategoryViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.nav_item_sub_category_name);
            expandIcon = itemView.findViewById(R.id.expand_icon);
        }
    }

    static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView questionText;
        LinearLayout statusIconContainer;
        QuestionViewHolder(View itemView) {
            super(itemView);
            questionText = itemView.findViewById(R.id.nav_item_question_text);
            statusIconContainer = itemView.findViewById(R.id.status_icon_container);
        }
    }
}