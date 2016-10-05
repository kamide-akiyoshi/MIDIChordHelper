package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
	public static final Icon MIDI_CONNECTER_ICON = new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	public static final String MSGS = "Microsoft GS Wavetable Synth";
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
	 * @param deviceModelManager デバイスモデルマネージャ
	 */
	public MidiDeviceDialog(final MidiDeviceModelManager deviceModelManager) {
		setTitle(openAction.getValue(Action.NAME).toString());
		setBounds( 300, 300, 820, 540 );
		MidiDeviceTreeView deviceTreeView = new MidiDeviceTreeView(deviceModelManager.getTreeModel());
		final MidiDeviceInfoPane deviceInfoPane = new MidiDeviceInfoPane();
		deviceTreeView.addTreeSelectionListener(deviceInfoPane);
		MidiDeviceDesktopPane desktopPane = new MidiDeviceDesktopPane(deviceTreeView, deviceInfoPane, this);
		deviceTreeView.addTreeSelectionListener(desktopPane);
		deviceTreeView.setSelectionRow(0);
		add(new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			new JSplitPane(
				JSplitPane.VERTICAL_SPLIT,
				new JScrollPane(deviceTreeView),
				new JPanel() {{
					add(new JPanel() {{
						add(new JButton("Detect USB MIDI devices", new ButtonIcon(ButtonIcon.REPEAT_ICON)) {{
							setToolTipText("Update view for USB MIDI device newly plugged or removed");
							addActionListener(new ActionListener() {
								@Override
								public void actionPerformed(ActionEvent e) {
									deviceModelManager.updateMidiDeviceList();
									deviceTreeView.expandAll();
								}
							});
						}});
						add(new JButton("Reset Tx timestamp", new ButtonIcon(ButtonIcon.TOP_ICON)) {{
							setToolTipText("Reset timestamp on transmittable MIDI devices");
							addActionListener(new ActionListener() {
								@Override
								public void actionPerformed(ActionEvent e) {
									deviceModelManager.resetMicrosecondPosition();
								}
							});
						}});
						setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
					}});
					add(new JScrollPane(deviceInfoPane));
					setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
				}}
			){{
				setDividerLocation(230);
			}},
			desktopPane
		){{
			setOneTouchExpandable(true);
			setDividerLocation(260);
		}});
	}
}
