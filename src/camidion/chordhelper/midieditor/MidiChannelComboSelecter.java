package camidion.chordhelper.midieditor;

import java.awt.FlowLayout;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * MIDIチャンネル選択ビュー（コンボボックス）
 */
public class MidiChannelComboSelecter extends JPanel {
	public JComboBox<Integer> comboBox = new JComboBox<>();
	public MidiChannelComboSelecter( String title ) {
		this(title, new DefaultMidiChannelComboBoxModel());
	}
	public MidiChannelComboSelecter(String title, MidiChannelComboBoxModel model) {
		setLayout(new FlowLayout());
		if( title != null ) add( new JLabel(title) );
		comboBox.setModel(model);
		comboBox.setMaximumRowCount(16);
		add(comboBox);
	}
	public JComboBox<Integer> getComboBox() {
		return comboBox;
	}
	public MidiChannelComboBoxModel getModel() {
		return (MidiChannelComboBoxModel)comboBox.getModel();
	}
	public int getSelectedChannel() {
		return comboBox.getSelectedIndex();
	}
	public void setSelectedChannel(int channel) {
		comboBox.setSelectedIndex(channel);
	}
}