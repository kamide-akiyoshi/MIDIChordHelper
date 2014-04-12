package camidion.chordhelper.midieditor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import camidion.chordhelper.pianokeyboard.PianoKeyboard;

/**
 * MIDIチャンネル選択ビュー（リストボタン）
 */
public class MidiChannelButtonSelecter extends JList<Integer>
	implements ListDataListener, ListSelectionListener
{
	private PianoKeyboard keyboard = null;
	public MidiChannelButtonSelecter(MidiChannelComboBoxModel model) {
		super(model);
		setLayoutOrientation(HORIZONTAL_WRAP);
		setVisibleRowCount(1);
		setCellRenderer(new MyCellRenderer());
		setSelectedIndex(model.getSelectedChannel());
		model.addListDataListener(this);
		addListSelectionListener(this);
	}
	@Override
	public void contentsChanged(ListDataEvent e) {
		setSelectedIndex(getModel().getSelectedChannel());
	}
	@Override
	public void intervalAdded(ListDataEvent e) {}
	@Override
	public void intervalRemoved(ListDataEvent e) {}
	@Override
	public void valueChanged(ListSelectionEvent e) {
		getModel().setSelectedChannel(getSelectedIndex());
	}
	public MidiChannelButtonSelecter(PianoKeyboard keyboard) {
		this(keyboard.midiChComboboxModel);
		setPianoKeyboard(keyboard);
	}
	@Override
	public MidiChannelComboBoxModel getModel() {
		return (MidiChannelComboBoxModel)(super.getModel());
	}
	public void setPianoKeyboard(PianoKeyboard keyboard) {
		(this.keyboard = keyboard).midiChannelButtonSelecter = this;
	}
	class MyCellRenderer extends JLabel implements ListCellRenderer<Integer> {
		private boolean cellHasFocus = false;
		public MyCellRenderer() {
			setOpaque(true);
			setHorizontalAlignment(CENTER);
			setSelectionBackground(Color.yellow);
		}
		@Override
		public Component getListCellRendererComponent(
			JList<? extends Integer> list,
			Integer value, int index,
			boolean isSelected, boolean cellHasFocus
		) {
			this.cellHasFocus = cellHasFocus;
			setText(value.toString());
			if(isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(
					keyboard != null && keyboard.countKeyOn(index) > 0 ?
					Color.pink : list.getBackground()
				);
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			return this;
		}
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if( cellHasFocus ) {
				g.setColor(Color.gray);
				g.drawRect(0, 0, this.getWidth() - 1, this.getHeight() - 1);
			}
		}
	}
}