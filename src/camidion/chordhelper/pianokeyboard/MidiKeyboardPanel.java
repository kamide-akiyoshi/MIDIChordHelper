package camidion.chordhelper.pianokeyboard;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.nio.charset.Charset;

import javax.sound.midi.MidiMessage;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import camidion.chordhelper.ChordDisplayLabel;
import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.chordmatrix.ChordMatrix;
import camidion.chordhelper.mididevice.VirtualMidiDevice;
import camidion.chordhelper.midieditor.KeySignatureSelecter;
import camidion.chordhelper.midieditor.MidiChannelButtonSelecter;
import camidion.chordhelper.midieditor.MidiChannelComboSelecter;
import camidion.chordhelper.midieditor.MidiEventDialog;
import camidion.chordhelper.midieditor.VelocitySelecter;
import camidion.chordhelper.music.Key;

public class MidiKeyboardPanel extends JPanel {
	private MidiEventDialog eventDialog;
	public void setEventDialog(MidiEventDialog eventDialog) {
		this.eventDialog = eventDialog;
	}
	JButton sendEventButton;
	JPanel keyboardChordPanel;
	JPanel keyboardSouthPanel;
	public KeySignatureSelecter keySelecter;
	public PianoKeyboardPanel keyboardCenterPanel;
	MidiChannelComboSelecter midiChannelCombobox;
	MidiChannelButtonSelecter midiChannelButtons;
	VelocitySelecter velocitySelecter;

	public void setCapoKey(Key key) {
		keySelecter.setSelectedKey(key);
		keyboardCenterPanel.keyboard.setKeySignature(key);
	}

	public MidiKeyboardPanel(ChordMatrix chordMatrix) {
		keyboardCenterPanel = new PianoKeyboardPanel();
		keyboardCenterPanel.keyboard.chordMatrix = chordMatrix;
		keyboardCenterPanel.keyboard.chordDisplay =
			new ChordDisplayLabel("MIDI Keyboard", null, chordMatrix, keyboardCenterPanel.keyboard);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(keyboardChordPanel = new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
				add(Box.createHorizontalStrut(5));
				add(velocitySelecter = new VelocitySelecter(keyboardCenterPanel.keyboard.velocityModel));
				add(keySelecter = new KeySignatureSelecter(Key.C_MAJOR_OR_A_MINOR));
				add(keyboardCenterPanel.keyboard.chordDisplay);
				add(Box.createHorizontalStrut(5));
			}
		});
		add(keyboardCenterPanel);
		add(Box.createVerticalStrut(5));
		add(keyboardSouthPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add(midiChannelCombobox = new MidiChannelComboSelecter(
				"MIDI Channel", keyboardCenterPanel.keyboard.midiChComboboxModel
			));
			add(midiChannelButtons = new MidiChannelButtonSelecter(
				keyboardCenterPanel.keyboard
			));
			add(sendEventButton = new JButton(new AbstractAction() {
				{ putValue(NAME,"Send MIDI event"); }
				@Override
				public void actionPerformed(ActionEvent e) {
					eventDialog.openMessageForm(
						"Send MIDI event",
						new AbstractAction() {
							{ putValue(NAME,"Send"); }
							@Override
							public void actionPerformed(ActionEvent e) {
								VirtualMidiDevice vmd = keyboardCenterPanel.keyboard.midiDevice;
								MidiMessage msg = eventDialog.midiMessageForm.getMessage(Charset.defaultCharset());
								if( msg != null ) vmd.sendMidiMessage(msg);
							}
						},
						keyboardCenterPanel.keyboard.midiChComboboxModel.getSelectedChannel()
					);
				}
			}) {
				{ setMargin(ChordHelperApplet.ZERO_INSETS); }
			});
		}});
	}

	public void setDarkMode(boolean isDark) {
		Color col = isDark ? Color.black : null;
		setBackground(col);
		keyboardCenterPanel.setDarkMode(isDark);
		keyboardChordPanel.setBackground(col);
		keyboardSouthPanel.setBackground(col);
		midiChannelButtons.setBackground(col);
		midiChannelCombobox.setBackground(col);
		midiChannelCombobox.comboBox.setBackground(col);
		keySelecter.setBackground(col);
		keySelecter.getKeysigCombobox().setBackground(col);
		velocitySelecter.setBackground(col);
		keyboardCenterPanel.keyboard.chordDisplay.setDarkMode(isDark);
		sendEventButton.setBackground(col);
	}

}