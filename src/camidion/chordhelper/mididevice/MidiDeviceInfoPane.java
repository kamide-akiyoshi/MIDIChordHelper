package camidion.chordhelper.mididevice;

import java.beans.PropertyVetoException;

import javax.sound.midi.MidiDevice;
import javax.swing.JEditorPane;
import javax.swing.JInternalFrame;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/**
 * MIDIデバイス情報表示エリア
 */
public class MidiDeviceInfoPane extends JEditorPane implements TreeSelectionListener {
	private void setTreeNodeText(Object node) {
		String html = "<html><head></head><body>";
		if( node instanceof MidiDeviceModel ) {
			MidiDeviceModel deviceModel = (MidiDeviceModel)node;
			MidiDevice device = deviceModel.getMidiDevice();
			MidiDevice.Info info = device.getDeviceInfo();
			html += "<b>"+deviceModel+"</b><br/>"
				+ "<table border=\"1\"><tbody>"
				+ "<tr><th>Version</th><td>"+info.getVersion()+"</td></tr>"
				+ "<tr><th>Description</th><td>"+info.getDescription()+"</td></tr>"
				+ "<tr><th>Vendor</th><td>"+info.getVendor()+"</td></tr>"
				+ "<tr><th>Status</th><td>"+(device.isOpen()?"Opened":"Closed")+"</td></tr>"
				+ "</tbody></table>";
		}
		else if( node instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)node;
			html += "<b>"+ioType+"</b><br/>";
			html += ioType.getDescription()+"<br/>";
		}
		else if( node != null ) {
			html += node.toString();
		}
		html += "</body></html>";
		setText(html);
	}
	/**
	 *	{@link MidiDeviceFrame} の開閉によってデバイス情報を再描画するリスナー
	 */
	public InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameOpened(InternalFrameEvent e) {
			internalFrameActivated(e);
		}
		@Override
		public void internalFrameActivated(InternalFrameEvent e) {
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame ) ) return;
			setTreeNodeText(((MidiDeviceFrame)frame).getMidiDeviceModel());
		}
		@Override
		public void internalFrameClosing(InternalFrameEvent e) {
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame ) ) return;
			MidiDeviceModel m = ((MidiDeviceFrame)frame).getMidiDeviceModel();
			m.closeReceiver();
			MidiDevice device = m.getMidiDevice();
			device.close();
			if( ! device.isOpen() ) {
				try {
					// 選択されたまま閉じると、次に開いたときにinternalFrameActivatedが
					// 呼ばれなくなってしまうので、選択を解除する。
					frame.setSelected(false);
				} catch (PropertyVetoException pve) {
					pve.printStackTrace();
				}
				frame.setVisible(false);
			}
			setTreeNodeText(m);
		}
	};
	/**
	 * ツリー上で選択状態が変わったとき、表示対象のMIDIデバイスを切り替えます。
	 */
	@Override
	public void valueChanged(TreeSelectionEvent e) {
		TreePath treePath = e.getNewLeadSelectionPath();
		setTreeNodeText(treePath == null ? null : treePath.getLastPathComponent());
	}
	public MidiDeviceInfoPane() {
		super("text/html","<html></html>");
		setEditable(false);
	}
}
