package ru.playsoftware.j2meloader.cheat;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class CheatDialog extends Dialog {

    private final List<Object> activeObjects;
    private ArrayAdapter<MemoryScanner.ScanResult> adapter;
    private MemoryScanner.ScanResult selectedResult;

    public CheatDialog(@NonNull Context context, List<Object> activeObjects) {
        super(context);
        this.activeObjects = activeObjects;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getContext().getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvStatus = new TextView(getContext());
        tvStatus.setText("Memory Injector Status: Ready");

        EditText etValue = new EditText(getContext());
        etValue.setHint("Masukkan Nilai (Contoh: 9999)");
        etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        Button btnFirstScan = new Button(getContext());
        btnFirstScan.setText("First Scan");

        Button btnNextScan = new Button(getContext());
        btnNextScan.setText("Next Scan");

        ListView listView = new ListView(getContext());
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_single_choice, new ArrayList<MemoryScanner.ScanResult>());
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        EditText etInjectValue = new EditText(getContext());
        etInjectValue.setHint("Nilai Baru Inject");
        etInjectValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        CheckBox cbFreeze = new CheckBox(getContext());
        cbFreeze.setText("Lock / Freeze Value");

        Button btnInject = new Button(getContext());
        btnInject.setText("Inject Value");

        Button btnClear = new Button(getContext());
        btnClear.setText("Clear All");

        layout.addView(tvStatus);
        layout.addView(etValue);
        layout.addView(btnFirstScan);
        layout.addView(btnNextScan);
        layout.addView(listView);
        layout.addView(etInjectValue);
        layout.addView(cbFreeze);
        layout.addView(btnInject);
        layout.addView(btnClear);

        setContentView(layout);
        setTitle("Memory Cheat Injector");

        // First Scan
        btnFirstScan.setOnClickListener(v -> {
            String input = etValue.getText().toString().trim();
            if (input.isEmpty()) return;
            int val;
            try { val = Integer.parseInt(input); } catch (NumberFormatException e) { return; }
            List<MemoryScanner.ScanResult> results = MemoryScanner.firstScan(activeObjects, val);
            adapter.clear();
            adapter.addAll(results);
            tvStatus.setText("Found: " + results.size() + " address(es)");
        });

        // Next Scan
        btnNextScan.setOnClickListener(v -> {
            String input = etValue.getText().toString().trim();
            if (input.isEmpty()) return;
            int val;
            try { val = Integer.parseInt(input); } catch (NumberFormatException e) { return; }
            List<MemoryScanner.ScanResult> results = MemoryScanner.nextScan(val);
            adapter.clear();
            adapter.addAll(results);
            tvStatus.setText("Refined: " + results.size() + " address(es)");
        });

        // Select item
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedResult = adapter.getItem(position);
            if (selectedResult != null) {
                etInjectValue.setText(String.valueOf(selectedResult.value));
            }
        });

        // Inject
        btnInject.setOnClickListener(v -> {
            if (selectedResult == null) {
                Toast.makeText(getContext(), "Pilih address dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            String injectStr = etInjectValue.getText().toString().trim();
            if (injectStr.isEmpty()) return;
            int newVal;
            try { newVal = Integer.parseInt(injectStr); } catch (NumberFormatException e) { return; }

            if (cbFreeze.isChecked()) {
                MemoryScanner.freeze(selectedResult, newVal);
                Toast.makeText(getContext(), "Injected & Frozen!", Toast.LENGTH_SHORT).show();
            } else {
                MemoryScanner.unfreeze(selectedResult);
                MemoryScanner.inject(selectedResult, newVal);
                Toast.makeText(getContext(), "Injected!", Toast.LENGTH_SHORT).show();
            }
            adapter.notifyDataSetChanged();
        });

        // Clear
        btnClear.setOnClickListener(v -> {
            MemoryScanner.clearAll();
            adapter.clear();
            etValue.setText("");
            etInjectValue.setText("");
            tvStatus.setText("Cleared");
        });
    }
}
