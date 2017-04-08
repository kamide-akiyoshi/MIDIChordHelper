package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;

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
	private Action openAction = new AbstractAction() {
		{
			putValue(NAME, "MIDI device connection");
			putValue(SHORT_DESCRIPTION, "MIDIデバイス間の接続を編集");
			putValue(LARGE_ICON_KEY, MIDI_CONNECTER_ICON);
		}
		@Override
		public void actionPerformed(ActionEvent event) { setVisible(true); }
	};
	/**
	 * MIDIデバイスダイアログを開くアクションを返します。
	 */
	public Action getOpenAction() { return openAction; }
	/**
	 * MIDIデバイスダイアログを構築します。
	 * @param deviceTreeModel デバイスツリーモデル
	 */
	public MidiDeviceDialog(MidiDeviceTreeModel deviceTreeModel) {
		setTitle(openAction.getValue(Action.NAME).toString());
		setBounds( 300, 300, 820, 540 );
		MidiDeviceTreeView deviceTreeView = new MidiDeviceTreeView(deviceTreeModel);
		MidiDeviceInfoPane deviceInfoPane = new MidiDeviceInfoPane();
		deviceTreeView.addTreeSelectionListener(e->deviceInfoPane.setTreePath(e.getNewLeadSelectionPath()));
		MidiDeviceDesktopPane desktopPane = new MidiDeviceDesktopPane(deviceTreeView, deviceInfoPane);
		deviceTreeView.addTreeSelectionListener(e->desktopPane.setTreePath(e.getNewLeadSelectionPath()));
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
							addActionListener(e->{
								deviceTreeModel.update();
								deviceTreeView.expandAll();
							});
						}});
						add(new JButton("Reset Tx timestamp", new ButtonIcon(ButtonIcon.TOP_ICON)) {{
							setToolTipText("Reset timestamp on transmittable MIDI devices");
							addActionListener(e->deviceTreeModel.resetMicrosecondPosition());
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
