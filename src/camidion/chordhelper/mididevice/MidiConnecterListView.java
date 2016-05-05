package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import camidion.chordhelper.ButtonIcon;

/**
 * Transmitter(Tx)/Receiver(Rx) のリスト（view）
 *
 * <p>マウスで Tx からドラッグして Rx へドロップする機能を備えた
 * 仮想MIDI端子リストです。
 * </p>
 */
public class MidiConnecterListView extends JList<AutoCloseable> {
	public static final Icon MIDI_CONNECTER_ICON = new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	/**
	 * リストに登録されている仮想MIDI端子の描画ツール
	 */
	private static class CellRenderer extends JLabel implements ListCellRenderer<AutoCloseable> {
		public Component getListCellRendererComponent(
				JList<? extends AutoCloseable> list, AutoCloseable value, int index,
				boolean isSelected, boolean cellHasFocus)
		{
			String text;
			if( value == null ) text = null;
			else if( value instanceof Receiver ) text = "Rx";
			else if( value instanceof Transmitter ) text = "Tx";
			else text = value.toString();
			setText(text);
			setIcon(MIDI_CONNECTER_ICON);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}

	private static final DataFlavor transmitterFlavor = new DataFlavor(Transmitter.class, "Transmitter");
	/**
	 * ドラッグ対象を表すクラス
	 */
	private static class DraggingObject implements Transferable {
		private static final DataFlavor flavors[] = {transmitterFlavor};
		private Transmitter tx;
		@Override
		public Object getTransferData(DataFlavor flavor) { return tx; }
		@Override
		public DataFlavor[] getTransferDataFlavors() { return flavors; }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return flavor.equals(transmitterFlavor);
		}
	};
	private static DraggingObject draggingObject = new DraggingObject();

	/**
	 * 現在ドラッグされているトランスミッタを返します。
	 * @return 現在ドラッグされているトランスミッタ（ドラッグ中でなければnull）
	 */
	public Transmitter getDraggingTransmitter() { return draggingObject.tx; }

	private MidiCablePane cablePane;
	private DragSourceListener dragSourceListener = new DragSourceAdapter() {
		@Override
		public void dragDropEnd(DragSourceDropEvent dsde) {
			if( ! dsde.getDropSuccess() ) getModel().closeTransmitter(getDraggingTransmitter());
			draggingObject.tx = null;
			cablePane.dragDropEnd();
		}
	};

	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public MidiConnecterListView(MidiConnecterListModel model, MidiCablePane cablePane) {
		super(model);
		this.cablePane = cablePane;
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		DragSource dragSource = new DragSource();
		dragSource.createDefaultDragGestureRecognizer(this,
			DnDConstants.ACTION_COPY_OR_MOVE,
			new DragGestureListener() {
				@Override
				public void dragGestureRecognized(DragGestureEvent dge) {
					if( (dge.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
					Point dragStartPoint = dge.getDragOrigin();
					AutoCloseable transceiver = getModel().getElementAt(locationToIndex(dragStartPoint));
					if( transceiver instanceof Transmitter ) {
						if( transceiver instanceof MidiConnecterListModel.NewTransmitter ) {
							draggingObject.tx = getModel().openTransmitter();
						}
						else {
							draggingObject.tx = (Transmitter)transceiver;
						}
						dge.startDrag(DragSource.DefaultLinkDrop, draggingObject, dragSourceListener);
					}
				}
			}
		);
		dragSource.addDragSourceMotionListener(cablePane.midiConnecterMotionListener);
		DropTargetAdapter dta= new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(transmitterFlavor) )
					event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
			}
			@Override
			public void drop(DropTargetDropEvent event) {
				event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				try {
					int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
					if( maskedBits == 0 || ! getModel().rxSupported() ) {
						event.dropComplete(false);
						return;
					}
					int index = locationToIndex(event.getLocation());
					AutoCloseable destination = getModel().getElementAt(index);
					if( ! (destination instanceof Receiver) ) {
						event.dropComplete(false);
						return;
					}
					Object source = event.getTransferable().getTransferData(transmitterFlavor);
					if( ! (source instanceof Transmitter) ) {
						event.dropComplete(false);
						return;
					}
					if( getModel().ConnectToReceiver((Transmitter)source, (Receiver)destination) == null ) {
						event.dropComplete(false);
						return;
					}
					event.dropComplete(true);
				}
				catch (Exception ex) {
					ex.printStackTrace();
					event.dropComplete(false);
				}
			}
		};
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dta, true );
	}
	@Override
	public MidiConnecterListModel getModel() { return (MidiConnecterListModel)super.getModel(); }
	/**
	 * 指定されたMIDI端子（Transmitter または Receiver）が仮想MIDI端子リスト上にあれば、
	 * そのセル範囲の矩形を返します。
	 *
	 * @param transceiver MIDI端子
	 * @return セル範囲の矩形（ない場合はnull）
	 */
	public Rectangle getCellBounds(AutoCloseable transceiver) {
		int index = getModel().indexOf(transceiver);
		Rectangle rect = getCellBounds(index,index);
		return rect == null ? null : rect;
	}
}
