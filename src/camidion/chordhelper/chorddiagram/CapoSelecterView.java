package camidion.chordhelper.chorddiagram;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * カポ選択ビュー
 */
public class CapoSelecterView extends JPanel {
	/**
	 * カポON/OFFチェックボックス
	 */
	public JCheckBox checkbox = new JCheckBox("Capo") {{ setOpaque(false); }};
	/**
	 * カポ位置選択コンボボックス
	 */
	public JComboBox<Integer> valueSelecter = new JComboBox<Integer>() {{
		setMaximumRowCount(12);
		setVisible(false);
	}};
	/**
	 * カポ選択ビューを構築します。
	 */
	public CapoSelecterView() {
		checkbox.addItemListener(e->valueSelecter.setVisible(checkbox.isSelected()));
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(checkbox);
		add(valueSelecter);
	}
	/**
	 * 指定されたデータモデルを操作するカポ選択ビューを構築します。
	 * @param model データモデル
	 */
	public CapoSelecterView(CapoComboBoxModel model) {
		this();
		valueSelecter.setModel(model);
	}
	/**
	 * カポ位置を返します。
	 * @return カポ位置
	 */
	public int getCapo() {
		return checkbox.isSelected() ? (int) valueSelecter.getModel().getSelectedItem() : 0;
	}
}