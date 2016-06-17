package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * MIDIレシーバ（{@link Receiver}）のリストビューです。
 * レシーバをこのビューからドラッグし、
 * {@link MidiTransmitterListView} のトランスミッタにドロップして接続できます。
 */
public class MidiReceiverListView extends JList<Receiver> {
	/**
	 * レシーバを描画するクラス
	 */
	private static class CellRenderer extends JLabel implements ListCellRenderer<Receiver> {
		public Component getListCellRendererComponent(JList<? extends Receiver> list,
				Receiver value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
			setToolTipText("ドラッグ＆ドロップしてTxに接続");
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			return this;
		}
	}
	/**
	 * ドラッグ対象レシーバを表すクラス
	 */
	private static class DraggingReceiver implements Transferable {
		private static final List<DataFlavor> flavors = Arrays.asList(MidiDeviceDialog.receiverFlavor);
		private Receiver rx;
		@Override
		public Object getTransferData(DataFlavor flavor) {
			return flavor.getRepresentationClass().isInstance(rx) ? rx : null;
		}
		@Override
		public DataFlavor[] getTransferDataFlavors() { return (DataFlavor[]) flavors.toArray(); }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) { return flavors.contains(flavor); }
	};
	private static DraggingReceiver draggingReceiver = new DraggingReceiver();

	/**
	 * 現在ドラッグされている{@link Receiver}を返します。
	 * ドラッグ中でなければnullを返します。
	 */
	public Receiver getDraggingReceiver() { return draggingReceiver.rx; }

	private MidiCablePane cablePane;
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public MidiReceiverListView(MidiDeviceModel.ReceiverListModel model, MidiCablePane cablePane) {
		super(model);
		this.cablePane = cablePane;
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		//
		// レシーバのドラッグを受け付ける
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				draggingReceiver.rx = getModel().getElementAt(locationToIndex(event.getDragOrigin()));
				event.startDrag(DragSource.DefaultLinkDrop, draggingReceiver, new DragSourceAdapter() {
					@Override
					public void dragDropEnd(DragSourceDropEvent event) {
						draggingReceiver.rx = null;
						MidiReceiverListView.this.cablePane.dragDropEnd();
					}
				});
			}
		};
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl);
		dragSource.addDragSourceMotionListener(cablePane);
		//
		// トランスミッタのドロップを受け付ける
		DropTargetListener dtl = new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(MidiDeviceDialog.transmitterFlavor) ) {
					event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
				}
			}
			@Override
			public void drop(DropTargetDropEvent event) {
				event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				try {
					int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
					if( maskedBits == 0 ) {
						event.dropComplete(false);
						return;
					}
					Transferable t = event.getTransferable();
					if( ! t.isDataFlavorSupported(MidiDeviceDialog.transmitterFlavor) ) {
						event.dropComplete(false);
						return;
					}
					Object source = t.getTransferData(MidiDeviceDialog.transmitterFlavor);
					if( ! (source instanceof Transmitter) ) {
						event.dropComplete(false);
						return;
					}
					Receiver destRx = getModel().getElementAt(locationToIndex(event.getLocation()));
					((Transmitter)source).setReceiver(destRx);
					event.dropComplete(true);
					return;
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
				event.dropComplete(false);
			}
		};
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dtl, true );
	}
	@Override
	public MidiDeviceModel.ReceiverListModel getModel() {
		return (MidiDeviceModel.ReceiverListModel) super.getModel();
	}
	/**
	 * 引数で指定された{@link Receiver}のセル範囲を示す、
	 * リストの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getCellBounds(Receiver rx) {
		int index = getModel().indexOf(rx);
		return getCellBounds(index,index);
	}
}
