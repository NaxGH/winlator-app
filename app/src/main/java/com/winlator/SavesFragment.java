package com.winlator;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.TarCompressorUtils;
import com.winlator.saves.SaveEntry;
import com.winlator.saves.SavesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavesFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private SavesManager manager;
    private ContainerManager containerManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        manager = new SavesManager(requireContext());
        containerManager = new ContainerManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.saves_fragment, container, false);
        recyclerView = view.findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        DividerItemDecoration divider = new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.list_item_divider);
        if (dividerDrawable != null) divider.setDrawable(dividerDrawable);
        recyclerView.addItemDecoration(divider);
        emptyTextView = view.findViewById(R.id.TVEmptyText);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ActionBar bar = ((AppCompatActivity)requireActivity()).getSupportActionBar();
        if (bar != null) bar.setTitle(R.string.saves);
        refreshContent();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.saves_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_item_add) {
            showRegisterDialog();
            return true;
        }
        else if (itemId == R.id.menu_item_import_save) {
            pickArchive();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshContent() {
        recyclerView.setAdapter(new SavesAdapter(manager.getSaves()));
        emptyTextView.setVisibility(manager.getSaves().isEmpty() ? View.VISIBLE : View.GONE);
    }

    private File getDriveCDir(Container container) {
        return new File(container.getRootDir(), ".wine/drive_c");
    }

    private void showRegisterDialog() {
        final Context context = getContext();
        if (context == null || noContainers()) return;
        final ArrayList<Container> containers = containerManager.getContainers();

        final ContentDialog dialog = new ContentDialog(context, R.layout.register_save_dialog);
        dialog.setTitle(R.string.register_a_save);
        dialog.setIcon(R.drawable.icon_export);
        dialog.findViewById(R.id.LLContent).getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);

        final EditText etName = dialog.findViewById(R.id.ETSaveName);
        final Spinner sContainer = dialog.findViewById(R.id.SContainer);
        final Button btLocate = dialog.findViewById(R.id.BTLocateSaveFolder);
        final Button btConfirm = dialog.findViewById(R.id.BTConfirm);

        final ColorStateList locateDefaultColors = btLocate.getTextColors();
        final int errorColor = 0xFFE53935;

        ArrayList<String> names = new ArrayList<>();
        for (Container c : containers) names.add(c.getName());
        sContainer.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, names));

        final String[] selectedPath = {null};

        sContainer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPath[0] = null;
                btLocate.setText(R.string.locate_save_folder);
                btLocate.setTextColor(locateDefaultColors);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btLocate.setOnClickListener((v) -> {
            Container selected = containers.get(sContainer.getSelectedItemPosition());
            showFolderPicker(getDriveCDir(selected), (relativePath) -> {
                selectedPath[0] = relativePath;
                btLocate.setText("C:/" + relativePath);
                btLocate.setTextColor(locateDefaultColors);
            });
        });

        btConfirm.setOnClickListener((v) -> {
            String name = etName.getText().toString().trim();
            boolean nameMissing = name.isEmpty();
            boolean folderMissing = selectedPath[0] == null;

            if (nameMissing) {
                etName.setError(context.getString(R.string.save_enter_a_name));
                etName.requestFocus();
            }
            if (folderMissing) {
                btLocate.setTextColor(errorColor);
                if (!nameMissing) AppUtils.showToast(context, R.string.save_select_a_folder);
            }
            if (nameMissing || folderMissing) return;

            Container selected = containers.get(sContainer.getSelectedItemPosition());
            manager.add(new SaveEntry(System.currentTimeMillis(), name, selected.id, selectedPath[0]));
            refreshContent();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showFolderPicker(final File root, final Callback<String> onSelected) {
        final Context context = getContext();
        if (context == null) return;

        final File[] current = {root};
        final ArrayList<File> rows = new ArrayList<>();
        final boolean[] hasBack = {false};

        final ContentDialog dialog = new ContentDialog(context);
        final ListView listView = dialog.findViewById(R.id.ListView);
        final Button confirmButton = dialog.findViewById(R.id.BTConfirm);
        final TextView tvTitle = dialog.findViewById(R.id.TVTitle);

        confirmButton.setText(R.string.choose);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.START);

        final ArrayAdapter<File> adapter = new ArrayAdapter<File>(context, R.layout.folder_picker_item, R.id.TVName, rows) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView
                        : LayoutInflater.from(context).inflate(R.layout.folder_picker_item, parent, false);
                File file = getItem(position);
                if (file == null) return view;
                boolean isBack = hasBack[0] && position == 0;

                ((ImageView)view.findViewById(R.id.IVIcon)).setImageResource(isBack ? R.drawable.icon_action_bar_back : R.drawable.icon_popup_menu_folder_open);
                ((TextView)view.findViewById(R.id.TVName)).setText(file.equals(root) ? context.getString(R.string.root) : file.getName());
                return view;
            }
        };

        listView.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);
        listView.setAdapter(adapter);
        listView.setVisibility(View.VISIBLE);

        final Runnable refresh = () -> {
            rows.clear();
            boolean atRoot = root.equals(current[0]);
            hasBack[0] = !atRoot;

            if (!atRoot) {
                File parent = current[0].getParentFile();
                if (parent != null) rows.add(parent);
                else hasBack[0] = false;
            }

            File[] children = current[0].listFiles(File::isDirectory);
            if (children != null) {
                Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                rows.addAll(Arrays.asList(children));
            }

            adapter.notifyDataSetChanged();
            dialog.setTitle(atRoot ? context.getString(R.string.root) : current[0].getName());
            confirmButton.setEnabled(!atRoot);
            confirmButton.setAlpha(atRoot ? 0.4f : 1f);
        };

        listView.setOnItemClickListener((parent, view, position, id) -> {
            current[0] = rows.get(position);
            refresh.run();
        });

        dialog.setOnConfirmCallback(() -> {
            String relative = current[0].getPath().substring(root.getPath().length());
            if (relative.startsWith("/")) relative = relative.substring(1);
            onSelected.call(relative);
        });

        refresh.run();
        dialog.show();
    }

    private class SavesAdapter extends RecyclerView.Adapter<SavesAdapter.ViewHolder> {
        private final List<SaveEntry> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final View runButton;
            private final View menuButton;

            private ViewHolder(View view) {
                super(view);
                imageView = view.findViewById(R.id.ImageView);
                title = view.findViewById(R.id.TVTitle);
                subtitle = view.findViewById(R.id.TVSubtitle);
                runButton = view.findViewById(R.id.BTRun);
                menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        SavesAdapter(List<SaveEntry> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.file_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final SaveEntry item = data.get(position);
            Container container = containerManager.getContainerById(item.containerId);

            holder.imageView.setImageResource(R.drawable.icon_saves);
            holder.title.setText(item.name);
            holder.subtitle.setText(container != null ? container.getName() : "?");
            holder.runButton.setVisibility(View.GONE);
            holder.menuButton.setOnClickListener((v) -> showItemMenu(v, item));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private void showItemMenu(View anchorView, final SaveEntry entry) {
        final Context context = requireContext();
        PopupMenu menu = new PopupMenu(context, anchorView);
        menu.inflate(R.menu.save_popup_menu);
        menu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.menu_item_export_save) {
                exportSave(entry);
            }
            else if (itemId == R.id.menu_item_unregister_save) {
                ContentDialog.confirm(context, R.string.do_you_want_to_unregister_this_save, () -> {
                    manager.remove(entry);
                    refreshContent();
                });
            }
            return true;
        });
        menu.show();
    }

    private File getSaveDir(SaveEntry entry) {
        Container container = containerManager.getContainerById(entry.containerId);
        if (container == null) return null;
        return new File(getDriveCDir(container), entry.relativePath);
    }

    private void exportSave(final SaveEntry entry) {
        final Context context = requireContext();
        final MainActivity activity = (MainActivity)requireActivity();
        final File saveDir = getSaveDir(entry);
        if (saveDir == null || !saveDir.isDirectory()) {
            AppUtils.showToast(context, R.string.save_folder_missing);
            return;
        }

        File outDir = new File(AppUtils.DIRECTORY_DOWNLOADS, "Winlator/Saves");
        if (!outDir.isDirectory() && !outDir.mkdirs() && !outDir.isDirectory()) {
            AppUtils.showToast(context, R.string.save_export_failed);
            return;
        }
        String safeName = entry.name.replaceAll("[^A-Za-z0-9._-]", "_");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        final File outFile = new File(outDir, safeName + "_" + timestamp + ".tar.xz");

        final PreloaderDialog preloader = activity.preloaderDialog;
        preloader.show(R.string.save_working);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File jsonFile = new File(context.getCacheDir(), saveDir.getName() + ".json");
            try {
                JSONObject meta = new JSONObject();
                meta.put("Title", entry.name);
                meta.put("Path", saveDir.getPath());
                meta.put("ContainerID", entry.containerId);
                FileUtils.writeString(jsonFile, meta.toString());
                TarCompressorUtils.compress(TarCompressorUtils.Type.XZ, new File[]{saveDir, jsonFile}, outFile, 3);
            }
            catch (JSONException e) {
            }
            FileUtils.delete(jsonFile);

            final boolean ok = outFile.isFile() && outFile.length() > 0;
            activity.runOnUiThread(() -> {
                preloader.close();
                AppUtils.showToast(context, ok ? R.string.save_export_done : R.string.save_export_failed);
            });
        });
        executor.shutdown();
    }

    private void pickArchive() {
        if (noContainers()) return;
        final MainActivity activity = (MainActivity)requireActivity();
        activity.setOpenFileCallback(this::showContainerPicker);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    private void showContainerPicker(final Uri uri) {
        final Context context = getContext();
        if (context == null || noContainers()) return;
        final ArrayList<Container> containers = containerManager.getContainers();

        String[] names = new String[containers.size()];
        for (int i = 0; i < names.length; i++) names[i] = containers.get(i).getName();

        final ContentDialog dialog = new ContentDialog(context);
        final ListView listView = dialog.findViewById(R.id.ListView);
        listView.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(new ArrayAdapter<>(context, R.layout.simple_list_item_single_choice, names));
        listView.setItemChecked(0, true); // must come after setAdapter
        listView.setVisibility(View.VISIBLE);

        dialog.setTitle(R.string.save_import_pick_container);
        dialog.setOnConfirmCallback(() -> {
            int position = listView.getCheckedItemPosition();
            if (position == ListView.INVALID_POSITION) return;

            final Container container = containers.get(position);
            ContentDialog.confirm(context, R.string.save_import_overwrite_warning, () -> importArchive(container, uri));
        });

        dialog.show();
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        if (!dst.isDirectory() && !dst.mkdirs()) throw new IOException("Unable to create " + dst);
        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (FileUtils.isSymlink(child)) continue;
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target);
            }
            else {
                try (InputStream in = Files.newInputStream(child.toPath());
                     OutputStream out = Files.newOutputStream(target.toPath())) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                FileUtils.chmod(target, 0771);
            }
        }
        FileUtils.chmod(dst, 0771);
    }

    private void importArchive(final Container container, final Uri uri) {
        final Context context = requireContext();
        final MainActivity activity = (MainActivity)requireActivity();
        final PreloaderDialog preloader = activity.preloaderDialog;
        preloader.show(R.string.save_working);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            final File tempDir = new File(context.getCacheDir(), "save_import_" + System.currentTimeMillis());
            int messageId = R.string.save_import_failed;
            SaveEntry newEntry = null;

            try {
                if (!tempDir.mkdirs() && !tempDir.isDirectory()) throw new IOException("Unable to create " + tempDir);
                final String tempBase = tempDir.getCanonicalPath();
                boolean extracted = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, context, uri, tempDir, (file, size) -> {
                    try {
                        String path = file.getCanonicalPath();
                        if (!path.equals(tempBase) && !path.startsWith(tempBase + File.separator)) return null;
                        File parent = file.getParentFile();
                        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) return null;
                    }
                    catch (IOException e) {
                        return null;
                    }
                    return file;
                });

                if (extracted) {
                    File base = tempDir;
                    File[] top = tempDir.listFiles();
                    if (top != null && top.length == 1 && top[0].isDirectory()) base = top[0];

                    File root = null;
                    JSONObject meta = null;
                    File[] children = base.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            File metaFile = new File(base, child.getName() + ".json");
                            if (child.isDirectory() && metaFile.isFile()) {
                                root = child;
                                meta = new JSONObject(FileUtils.readString(metaFile));
                                break;
                            }
                        }
                    }

                    if (root == null) {
                        messageId = R.string.save_import_no_metadata;
                    }
                    else {
                        String path = meta.getString("Path");
                        int index = path.indexOf("/drive_c/");
                        if (index == -1) {
                            messageId = R.string.save_import_no_metadata;
                        }
                        else {
                            String relative = path.substring(index + 9);
                            File driveC = getDriveCDir(container);
                            File saveDir = new File(driveC, relative);
                            if (saveDir.getCanonicalPath().startsWith(driveC.getCanonicalPath() + File.separator)) {
                                copyDirectory(root, saveDir);
                                messageId = R.string.save_import_done;
                                newEntry = new SaveEntry(System.currentTimeMillis(), meta.optString("Title", saveDir.getName()), container.id, relative);
                            }
                        }
                    }
                }
            }
            catch (IOException | JSONException e) {
            }
            finally {
                FileUtils.delete(tempDir);
            }

            final int message = messageId;
            final SaveEntry added = messageId == R.string.save_import_done ? newEntry : null;
            activity.runOnUiThread(() -> {
                preloader.close();
                if (added != null) {
                    boolean exists = false;
                    for (SaveEntry e : manager.getSaves()) {
                        if (e.containerId == added.containerId && e.relativePath.equals(added.relativePath)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) manager.add(added);
                    refreshContent();
                }
                AppUtils.showToast(context, message);
            });
        });
    }

    private boolean noContainers() {
        if (!containerManager.getContainers().isEmpty()) return false;
        AppUtils.showToast(requireContext(), R.string.save_no_containers);
        return true;
    }
}