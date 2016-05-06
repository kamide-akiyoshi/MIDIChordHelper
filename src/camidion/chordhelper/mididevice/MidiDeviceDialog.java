package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.sound.midi.MidiDevice;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

/**
 * MIDIデバイスダイアログ (View)
 */
public class MidiDeviceDialog extends JDialog implements ActionListener {
	private JEditorPane deviceInfoPane;
	private MidiOpenedDevicesView desktopPane;
	@Override
	public void actionPerformed(ActionEvent event) { setVisible(true); }
	public MidiDeviceDialog(MidiDeviceModelList deviceModelList) {
		setTitle("MIDI device connection");
		setBounds( 300, 300, 800, 500 );
		MidiDeviceTreeView deviceTree = new MidiDeviceTreeView(new MidiDeviceTreeModel(deviceModelList)) {{
			addTreeSelectionListener(new TreeSelectionListener() {
				@Override
				public void valueChanged(TreeSelectionEvent e) {
					String html = "<html><head></head><body>";
					Object lastSelected = e.getNewLeadSelectionPath().getLastPathComponent();
					if( lastSelected instanceof MidiConnecterListModel ) {
						MidiConnecterListModel deviceModel = (MidiConnecterListModel)lastSelected;
						MidiDevice.Info info = deviceModel.getMidiDevice().getDeviceInfo();
						html += "<b>"+deviceModel+"</b><br/>"
							+ "<table border=\"1\"><tbody>"
							+ "<tr><th>Version</th><td>"+info.getVersion()+"</td></tr>"
							+ "<tr><th>Description</th><td>"+info.getDescription()+"</td></tr>"
							+ "<tr><th>Vendor</th><td>"+info.getVendor()+"</td></tr>"
							+ "</tbody></table>";
					}
					else if( lastSelected instanceof MidiDeviceInOutType ) {
						MidiDeviceInOutType ioType = (MidiDeviceInOutType)lastSelected;
						html += "<b>"+ioType+"</b><br/>";
						html += ioType.getDescription()+"<br/>";
					}
					else if( lastSelected != null ) {
						html += lastSelected.toString();
					}
					html += "</body></html>";
					deviceInfoPane.setText(html);
				}
			});
		}};
		deviceInfoPane = new JEditorPane("text/html","<html></html>") {{ setEditable(false); }};
		desktopPane = new MidiOpenedDevicesView(deviceTree);
		add(new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			new JSplitPane(
				JSplitPane.VERTICAL_SPLIT,
				new JScrollPane(deviceTree),
				new JScrollPane(deviceInfoPane)
			){{
				setDividerLocation(260);
			}},
			desktopPane
		){{
			setOneTouchExpandable(true);
			setDividerLocation(250);
		}});
		addWindowListener(new WindowAdapter() {
			private void setTimer(boolean flag) { desktopPane.setAllDeviceTimestampTimers(flag); }
			@Override
			public void windowClosing(WindowEvent e) { setTimer(false); }
			@Override
			public void windowActivated(WindowEvent e) { setTimer(true); }
		});
	}
}
