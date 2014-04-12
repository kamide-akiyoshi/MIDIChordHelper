package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;

import javax.swing.JTree;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;

/**
 * MIDIデバイスツリービュー
 */
public class MidiDeviceTree extends JTree
	implements Transferable, DragGestureListener, InternalFrameListener
{
	/**
	 * MIDIデバイスツリービューを構築します。
	 * @param model このビューにデータを提供するモデル
	 */
	public MidiDeviceTree(MidiDeviceTreeModel model) {
		super(model);
        (new DragSource()).createDefaultDragGestureRecognizer(
        	this, DnDConstants.ACTION_COPY_OR_MOVE, this
        );
        setCellRenderer(new DefaultTreeCellRenderer() {
    		@Override
    		public Component getTreeCellRendererComponent(
    			JTree tree, Object value,
    			boolean selected, boolean expanded, boolean leaf, int row,
    			boolean hasFocus
    		) {
    			super.getTreeCellRendererComponent(
    				tree, value, selected, expanded, leaf, row, hasFocus
    			);
    			if(leaf) {
   					setIcon(MidiConnecterListView.MIDI_CONNECTER_ICON);
   					setDisabledIcon(MidiConnecterListView.MIDI_CONNECTER_ICON);
    				MidiConnecterListModel listModel = (MidiConnecterListModel)value;
    				setEnabled( ! listModel.getMidiDevice().isOpen() );
    			}
    			return this;
    		}
    	});
	}
	/**
	 * このデバイスツリーからドラッグされるデータフレーバ
	 */
	public static final DataFlavor
		TREE_MODEL_FLAVOR = new DataFlavor(TreeModel.class, "TreeModel");
	private static final DataFlavor
		TREE_MODE_FLAVORS[] = {TREE_MODEL_FLAVOR};
	@Override
	public Object getTransferData(DataFlavor flavor) {
		return getLastSelectedPathComponent();
	}
	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return TREE_MODE_FLAVORS;
	}
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return flavor.equals(TREE_MODEL_FLAVOR);
	}
	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		int action = dge.getDragAction();
		if( (action & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
			dge.startDrag(DragSource.DefaultMoveDrop, this, null);
		}
	}
	@Override
	public void internalFrameOpened(InternalFrameEvent e) {}
	/**
	 * 	MidiDeviceFrame のクローズ処理中に再描画リクエストを送ります。
	 */
	@Override
	public void internalFrameClosing(InternalFrameEvent e) {
		repaint();
	}
	@Override
	public void internalFrameClosed(InternalFrameEvent e) {}
	@Override
	public void internalFrameIconified(InternalFrameEvent e) {}
	@Override
	public void internalFrameDeiconified(InternalFrameEvent e) {}
	@Override
	public void internalFrameActivated(InternalFrameEvent e) {}
	@Override
	public void internalFrameDeactivated(InternalFrameEvent e) {}
}