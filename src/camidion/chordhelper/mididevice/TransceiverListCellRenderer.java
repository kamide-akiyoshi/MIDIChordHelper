package camidion.chordhelper.mididevice;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class TransceiverListCellRenderer<E> extends JLabel implements ListCellRenderer<E> {
	public Component getListCellRendererComponent(JList<? extends E> list,
			E value, int index, boolean isSelected, boolean cellHasFocus)
	{
		setEnabled(list.isEnabled());
		setFont(list.getFont());
		setOpaque(true);
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		} else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}
		setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
		return this;
	}
}
