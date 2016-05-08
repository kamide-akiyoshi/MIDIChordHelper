package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

/**
 * MIDIデバイスダイアログ (View)
 */
public class MidiDeviceDialog extends JDialog implements ActionListener {
	@Override
	public void actionPerformed(ActionEvent event) { setVisible(true); }
	public MidiDeviceDialog(MidiTransceiverListModelList deviceModelList) {
		setTitle("MIDI device connection");
		setBounds( 300, 300, 800, 500 );
		MidiDeviceTreeModel deviceTreeModel = new MidiDeviceTreeModel(deviceModelList);
		MidiDeviceTreeView deviceTreeView = new MidiDeviceTreeView(deviceTreeModel);
		MidiDeviceInfoPane deviceInfoPane = new MidiDeviceInfoPane();
		MidiOpenedDevicesView desktopPane = new MidiOpenedDevicesView(deviceTreeView, deviceInfoPane, this);
		deviceTreeView.addTreeSelectionListener(deviceInfoPane);
		deviceTreeView.addTreeSelectionListener(desktopPane);
		add(new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			new JSplitPane(
				JSplitPane.VERTICAL_SPLIT,
				new JScrollPane(deviceTreeView),
				new JScrollPane(deviceInfoPane)
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
