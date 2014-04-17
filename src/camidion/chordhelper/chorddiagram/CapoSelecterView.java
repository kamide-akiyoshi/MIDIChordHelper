package camidion.chordhelper.chorddiagram;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * カポ選択ビュー
 */
public class CapoSelecterView extends JPanel implements ItemListener {
	/**
	 * カポON/OFFチェックボックス
	 */
	public JCheckBox checkbox = new JCheckBox("Capo") {
		{
			setOpaque(false);
		}
	};
	/**
	 * カポ位置選択コンボボックス
	 */
	public JComboBox<Integer> valueSelecter = new JComboBox<Integer>() {
		{
			setMaximumRowCount(12);
			setVisible(false);
		}
	};
	/**
	 * カポ選択ビューを構築します。
	 */
	public CapoSelecterView() {
		checkbox.addItemListener(this);
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(checkbox);
		add(valueSelecter);
	}
	/**
	 * 指定されたデータモデルを操作するカポ選択ビューを構築します。
	 * @param model データモデル
	 */
	public CapoSelecterView(ComboBoxModel<Integer> model) {
		this();
		valueSelecter.setModel(model);
	}
	@Override
	public void itemStateChanged(ItemEvent e) {
		valueSelecter.setVisible(checkbox.isSelected());
	}
	/**
	 * カポ位置を返します。
	 * @return カポ位置
	 */
	public int getCapo() {
		return checkbox.isSelected() ? valueSelecter.getSelectedIndex()+1 : 0;
	}
}