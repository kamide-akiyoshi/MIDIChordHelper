package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import camidion.chordhelper.ButtonIcon;

/**
 * MIDIデバイスダイアログ (View)
 */
public class MidiDeviceDialog extends JDialog {
	public static final DataFlavor receiverFlavor = new DataFlavor(Receiver.class, "Receiver");
	public static final DataFlavor transmitterFlavor = new DataFlavor(Transmitter.class, "Transmitter");
	public static final Icon MIDI_CONNECTER_ICON = new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	/**
	 * MIDIデバイスダイアログを開くアクション
	 */
	public Action openAction = new AbstractAction() {
		{
			putValue(NAME, "MIDI device connection");
			putValue(SHORT_DESCRIPTION, "MIDIデバイス間の接続を編集");
			putValue(LARGE_ICON_KEY, MIDI_CONNECTER_ICON);
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			setVisible(true);
		}
	};
	/**
	 * MIDIデバイスダイアログを構築します。
	 * @param deviceModelList デバイスモデル（MIDIコネクタリストモデル）のリスト
	 */
	public MidiDeviceDialog(final MidiDeviceModelList deviceModelList) {
		setTitle(openAction.getValue(Action.NAME).toString());
		setBounds( 300, 300, 800, 500 );
		MidiDeviceTreeModel deviceTreeModel = new MidiDeviceTreeModel(deviceModelList);
		MidiDeviceTreeView deviceTreeView = new MidiDeviceTreeView(deviceTreeModel);
		final MidiDeviceInfoPane deviceInfoPane = new MidiDeviceInfoPane();
		deviceTreeView.addTreeSelectionListener(deviceInfoPane);
		MidiOpenedDevicesView desktopPane = new MidiOpenedDevicesView(deviceTreeView, deviceInfoPane, this);
		deviceTreeView.addTreeSelectionListener(desktopPane);
		deviceTreeView.setSelectionRow(0);
		add(new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			new JSplitPane(
				JSplitPane.VERTICAL_SPLIT,
				new JScrollPane(deviceTreeView),
				new JPanel() {{
					add(new JScrollPane(deviceInfoPane));
					add(new JButton("Reset time on MIDI devices") {{
						addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								deviceModelList.resetMicrosecondPosition();
							}
						});
					}});
					setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
				}}
			){{
				setDividerLocation(260);
			}},
			desktopPane
		){{
			setOneTouchExpandable(true);
			setDividerLocation(250);
		}});
	}
}
