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
import java.awt.dnd.DragSourceListener;

import javax.swing.JInternalFrame;
import javax.swing.JTree;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;

/**
 * MIDIデバイスツリービュー
 */
public class MidiDeviceTreeView extends JTree {

	public static final DataFlavor TREE_FLAVOR  = new DataFlavor(TreeModel.class,"TreeModel");

	public class DraggingObject implements Transferable {
		private DataFlavor flavors[] = {TREE_FLAVOR};
		private MidiTransceiverListModel trxListModel;
		@Override
		public Object getTransferData(DataFlavor flavor) {
			return TREE_FLAVOR.equals(flavor) ? trxListModel : null;
		}
		@Override
		public DataFlavor[] getTransferDataFlavors() { return flavors; }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return TREE_FLAVOR.equals(flavor);
		}
	};
	private DraggingObject draggingObject = new DraggingObject();
	/**
	 * このツリーからドラッグしたMIDIデバイスのドロップが成功し、
	 * MIDIデバイスが開かれたときに再描画するためのリスナー
	 */
	private DragSourceListener dragSourceListener = new DragSourceAdapter() {
		@Override
		public void dragDropEnd(DragSourceDropEvent dsde) {
			if( dsde.getDropSuccess() ) repaint();
		}
	};
	/**
	 *	{@link MidiDeviceFrame} が閉じられたり、選択されたりしたときに再描画するリスナー
	 */
	public final InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameActivated(InternalFrameEvent e) {
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame ) ) return;
			setSelectionPath(((MidiDeviceFrame)frame).getMidiTransceiverListView().getModel().getTreePath());
		}
		@Override
		public void internalFrameClosing(InternalFrameEvent e) { repaint(); }
	};
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
					if( leaf instanceof MidiTransceiverListModel ) {
						draggingObject.trxListModel = (MidiTransceiverListModel)leaf;
						dge.startDrag(DragSource.DefaultMoveDrop, draggingObject, dragSourceListener);
					}
				}
			}
		);
		setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value,
					boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				if(leaf) {
					setIcon(MidiTransceiverListView.MIDI_CONNECTER_ICON);
					setDisabledIcon(MidiTransceiverListView.MIDI_CONNECTER_ICON);
					setEnabled( ! ((MidiTransceiverListModel)value).getMidiDevice().isOpen() );
				}
				return this;
			}
		});
		// 初期状態でツリーノードを開いた状態にする
		for( int row = 0; row < getRowCount() ; row++ ) expandRow(row);
	}
	@Override
	public MidiDeviceTreeModel getModel() { return (MidiDeviceTreeModel) super.getModel(); }
}
