package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;

import javax.swing.JInternalFrame;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * MIDIデバイスツリービュー
 */
public class MidiDeviceTreeView extends JTree {

	public static final DataFlavor DEVICE_MODEL_FLAVOR  = new DataFlavor(MidiDeviceModel.class,"MidiDeviceModel");

	public class DraggingDevice implements Transferable {
		private DataFlavor flavors[] = {DEVICE_MODEL_FLAVOR};
		private MidiDeviceModel midiDeviceModel;
		@Override
		public Object getTransferData(DataFlavor flavor) {
			return flavor.getRepresentationClass().isInstance(midiDeviceModel) ? midiDeviceModel : null;
		}
		@Override
		public DataFlavor[] getTransferDataFlavors() { return flavors; }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return flavors[0].equals(flavor);
		}
	};
	private DraggingDevice draggingDevice = new DraggingDevice();
	/**
	 *	{@link MidiDeviceFrame} が閉じられたり、選択されたりしたときに再描画するリスナー
	 */
	public final InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameActivated(InternalFrameEvent e) {
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame ) ) return;
			setSelectionPath(((MidiDeviceFrame)frame).getMidiDeviceModel().getTreePath());
		}
		@Override
		public void internalFrameClosing(InternalFrameEvent e) { repaint(); }
	};
	@Override
	public MidiDeviceTreeModel getModel() { return (MidiDeviceTreeModel) super.getModel(); }
	/**
	 * MIDIデバイスツリービューを構築します。
	 * @param model このビューにデータを提供するモデル
	 */
	public MidiDeviceTreeView(MidiDeviceTreeModel model) {
		super(model);
		(new DragSource()).createDefaultDragGestureRecognizer(
			this, DnDConstants.ACTION_COPY_OR_MOVE, new DragGestureListener() {
				@Override
				public void dragGestureRecognized(DragGestureEvent dge) {
					if( (dge.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
					Point origin = dge.getDragOrigin();
					Object leaf = getPathForLocation(origin.x, origin.y).getLastPathComponent();
					if( ! (leaf instanceof MidiDeviceModel) ) return;
					draggingDevice.midiDeviceModel = (MidiDeviceModel)leaf;
					dge.startDrag(DragSource.DefaultMoveDrop, draggingDevice, new DragSourceAdapter() {
						@Override
						public void dragDropEnd(DragSourceDropEvent dsde) {
							if( dsde.getDropSuccess() ) repaint();
						}
					});
				}
			}
		);
		setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value,
					boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				setToolTipText(value.toString());
				if(leaf) {
					MidiDeviceModel deviceModel = (MidiDeviceModel)value;
					if( deviceModel.getMidiDevice().isOpen() ) {
						setDisabledIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
						setEnabled(false);
						setToolTipText(getToolTipText()+"はすでに開いています");
					} else {
						setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
						setEnabled(true);
						setToolTipText("ドラッグ＆ドロップで"+getToolTipText()+"が開きます");
					}
				}
				return this;
			}
		});
		// ツリーノードを開き、ルートを選択した状態にする
		for( int row = 0; row < getRowCount() ; row++ ) expandRow(row);
		//
		// ツリーノードのToolTipを有効化
		ToolTipManager.sharedInstance().registerComponent(this);

	}
}
