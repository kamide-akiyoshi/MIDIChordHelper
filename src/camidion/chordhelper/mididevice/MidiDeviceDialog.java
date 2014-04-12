package camidion.chordhelper.mididevice;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.util.List;

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
public class MidiDeviceDialog extends JDialog
	implements ActionListener, TreeSelectionListener
{
	private JEditorPane deviceInfoPane;
	private MidiDesktopPane desktopPane;
	private MidiDeviceTree deviceTree;
	public MidiDeviceDialog(List<MidiConnecterListModel> deviceModelList) {
		setTitle("MIDI device connection");
		setBounds( 300, 300, 800, 500 );
		deviceTree = new MidiDeviceTree(new MidiDeviceTreeModel(deviceModelList));
		deviceTree.addTreeSelectionListener(this);
		deviceInfoPane = new JEditorPane("text/html","<html></html>") {
			{
				setEditable(false);
			}
		};
		desktopPane = new MidiDesktopPane(deviceTree);
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
			@Override
			public void windowClosing(WindowEvent e) {
				desktopPane.setAllDeviceTimestampTimers(false);
			}
			@Override
			public void windowActivated(WindowEvent e) {
				desktopPane.setAllDeviceTimestampTimers(true);
			}
		});
	}
	@Override
	public void actionPerformed(ActionEvent event) {
		setVisible(true);
	}
	@Override
	public void valueChanged(TreeSelectionEvent e) {
		Object lastSelected = deviceTree.getLastSelectedPathComponent();
		String html = "<html><head></head><body>";
		if( lastSelected instanceof MidiConnecterListModel ) {
			MidiConnecterListModel deviceModel = (MidiConnecterListModel)lastSelected;
			MidiDevice.Info info = deviceModel.getMidiDevice().getDeviceInfo();
			html += "<b>"+deviceModel+"</b><br/>"
				+ "<table border=\"1\"><tbody>"
				+ "<tr><th>Version</th><td>"+info.getVersion()+"</td></tr>"
				+ "<tr><th>Description</th><td>"+info.getDescription()+"</td></tr>"
				+ "<tr><th>Vendor</th><td>"+info.getVendor()+"</td></tr>"
				+ "</tbody></table>";
			MidiDeviceFrame frame = desktopPane.getFrameOf(deviceModel);
			if( frame != null ) {
				try {
					frame.setSelected(true);
				} catch( PropertyVetoException ex ) {
					ex.printStackTrace();
				}
			}
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
}
