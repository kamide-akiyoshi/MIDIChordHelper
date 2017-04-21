package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.util.Arrays;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;

import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.music.MIDISpec;

/**
 * シーケンス（トラックリスト）テーブルビュー
 */
public class SequenceTrackListTable extends JTable {
	/**
	 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
	 */
	public MidiEventTable eventListTable;
	/**
	 * トラックリストテーブルビューを構築します。
	 * @param model シーケンス（トラックリスト）データモデル
	 * @param eventListTable イベントリストテーブル
	 */
	public SequenceTrackListTable(SequenceTrackListTableModel model, MidiEventTable eventListTable) {
		super(model, null, model.getSelectionModel());
		this.eventListTable = eventListTable;
		//
		// 録音対象のMIDIチャンネルをコンボボックスで選択できるようにする
		getColumnModel()
			.getColumn(SequenceTrackListTableModel.Column.RECORD_CHANNEL.ordinal())
			.setCellEditor(new DefaultCellEditor(new JComboBox<String>(){{
				addItem("OFF");
				for(int i=1; i <= MIDISpec.MAX_CHANNELS; i++) addItem(String.format("%d", i));
				addItem("ALL");
			}}));
		setAutoCreateColumnsFromModel(false);
		model.getParent().sequenceListSelectionModel.addListSelectionListener(titleLabel);
		TableColumnModel colModel = getColumnModel();
		Arrays.stream(SequenceTrackListTableModel.Column.values()).forEach(c->
			colModel.getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth)
		);
	}
	/**
	 * このテーブルビューが表示するデータを提供する
	 * シーケンス（トラックリスト）データモデルを返します。
	 * @return シーケンス（トラックリスト）データモデル
	 */
	@Override
	public SequenceTrackListTableModel getModel() {
		return (SequenceTrackListTableModel)dataModel;
	}
	/**
	 * タイトルラベル
	 */
	TitleLabel titleLabel = new TitleLabel();
	/**
	 * 親テーブルの選択シーケンスの変更に反応する
	 * 曲番号表示付きタイトルラベル
	 */
	private class TitleLabel extends JLabel implements ListSelectionListener {
		private static final String TITLE = "Tracks";
		public TitleLabel() { setText(TITLE); }
		@Override
		public void valueChanged(ListSelectionEvent event) {
			if( event.getValueIsAdjusting() ) return;
			SequenceTrackListTableModel oldModel = getModel();
			SequenceTrackListTableModel newModel = oldModel.getParent().getSelectedSequenceModel();
			if( oldModel == newModel ) return;
			//
			// MIDIチャンネル選択中のときはキャンセルする
			cancelCellEditing();
			//
			String text = TITLE;
			ListSelectionModel sm = oldModel.getParent().sequenceListSelectionModel;
			if( ! sm.isSelectionEmpty() ) {
				int index = sm.getMinSelectionIndex();
				if( index >= 0 ) text = String.format(text+" - MIDI file #%d", index);
			}
			setText(text);
			if( newModel == null ) {
				newModel = oldModel.getParent().emptyTrackListTableModel;
				addTrackAction.setEnabled(false);
			}
			else {
				addTrackAction.setEnabled(true);
			}
			oldModel.getSelectionModel().removeListSelectionListener(trackSelectionListener);
			setModel(newModel);
			setSelectionModel(newModel.getSelectionModel());
			newModel.getSelectionModel().addListSelectionListener(trackSelectionListener);
		}
	}
	/**
	 * {@inheritDoc}
	 *
	 * <p>このトラックリストテーブルのデータが変わったときに編集を解除します。
	 * 例えば、イベントが編集された場合や、
	 * シーケンサーからこのモデルが外された場合がこれに該当します。
	 * </p>
	 */
	@Override
	public void tableChanged(TableModelEvent e) {
		super.tableChanged(e);
		cancelCellEditing();
	}
	/**
	 * このトラックリストテーブルが編集モードになっていたら解除します。
	 */
	private void cancelCellEditing() {
		TableCellEditor currentCellEditor = getCellEditor();
		if( currentCellEditor != null ) currentCellEditor.cancelCellEditing();
	}
	/**
	 * トラック追加アクション
	 */
	Action addTrackAction = new AbstractAction("New") {
		{
			String tooltip = "Append new track - 新しいトラックの追加";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
			setEnabled(false);
		}
		@Override
		public void actionPerformed(ActionEvent e) { getModel().createTrack(); }
	};
	/**
	 * トラック削除アクション
	 */
	Action deleteTrackAction = new AbstractAction("Delete", MidiSequenceEditorDialog.deleteIcon) {
		public static final String CONFIRM_MESSAGE =
				"Do you want to delete selected track ?\n選択したトラックを削除しますか？";
		{
			putValue(Action.SHORT_DESCRIPTION, "Delete selected track - 選択したトラックを削除");
			setEnabled(false);
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			if( JOptionPane.showConfirmDialog(
					((JComponent)event.getSource()).getRootPane(),
					CONFIRM_MESSAGE,
					ChordHelperApplet.VersionInfo.NAME,
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
			) getModel().deleteSelectedTracks();
		}
	};
	/**
	 * トラック選択リスナー
	 */
	private ListSelectionListener trackSelectionListener = event->{
		if( event.getValueIsAdjusting() ) return;
		ListSelectionModel selModel = getModel().getSelectionModel();
		deleteTrackAction.setEnabled(! selModel.isSelectionEmpty());
		eventListTable.titleLabel.updateTrackNumber(selModel.getMinSelectionIndex());
		eventListTable.setModel(getModel().getSelectedTrackModel());
	};
}