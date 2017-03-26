package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.music.ChordProgression;

/**
 * プレイリスト（MIDIシーケンスリスト）のテーブルデータモデル
 */
public class PlaylistTableModel extends AbstractTableModel {
	private MidiSequencerModel sequencerModel;
	/**
	 * このプレイリストと連携しているMIDIシーケンサモデルを返します。
	 */
	public MidiSequencerModel getSequencerModel() { return sequencerModel; }
	/**
	 * 空のトラックリストモデル
	 */
	SequenceTrackListTableModel emptyTrackListTableModel = new SequenceTrackListTableModel(this, null, null);
	/**
	 * 空のイベントリストモデル
	 */
	TrackEventListTableModel emptyEventListTableModel = new TrackEventListTableModel(emptyTrackListTableModel, null);
	/**
	 * 選択されているシーケンスのインデックス
	 */
	public ListSelectionModel sequenceListSelectionModel = new DefaultListSelectionModel() {
		{
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		}
	};
	/** 再生中のシーケンサーの秒位置リスナー */
	private ChangeListener mmssPosition = new ChangeListener() {
		private int value = 0;
		@Override
		public void stateChanged(ChangeEvent event) {
			Object src = event.getSource();
			if( src instanceof MidiSequencerModel ) {
				int newValue = ((MidiSequencerModel)src).getValue() / 1000;
				if(value != newValue) {
					value = newValue;
					fireTableCellUpdated(indexOfSequenceOnSequencer(), Column.POSITION.ordinal());
				}
			}
		}
		@Override
		public String toString() {
			return String.format("%02d:%02d", value/60, value%60);
		}
	};
	/**
	 * 新しいプレイリストのテーブルモデルを構築します。
	 * @param sequencerModel 連携するMIDIシーケンサーモデル
	 */
	public PlaylistTableModel(MidiSequencerModel sequencerModel) {
		this.sequencerModel = sequencerModel;
		sequencerModel.addChangeListener(mmssPosition);
		sequencerModel.getSequencer().addMetaEventListener(msg->{
			// EOF(0x2F)が来て曲が終わったら次の曲へ進める
			if(msg.getType() == 0x2F) SwingUtilities.invokeLater(()->{
				try {
					goNext();
				} catch (InvalidMidiDataException e) {
					throw new RuntimeException("Could not play next sequence after end-of-track",e);
				}
			});
		});
	}
	/**
	 * 次の曲へ進みます。
	 *
	 * <p>リピートモードの場合は同じ曲をもう一度再生、そうでない場合は次の曲へ進んで再生します。
	 * 次の曲がなければ、そこで停止します。いずれの場合も曲の先頭へ戻ります。
	 * </p>
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	private void goNext() throws InvalidMidiDataException {
		// とりあえず曲の先頭へ戻る
		sequencerModel.getSequencer().setMicrosecondPosition(0);
		if( (Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY) || loadNext(1) ) {
			// リピートモードのときはもう一度同じ曲を、そうでない場合は次の曲を再生開始
			sequencerModel.start();
		}
		else {
			// 最後の曲が終わったので、停止状態にする
			sequencerModel.stop();
			// ここでボタンが停止状態に変わったはずなので、通常であれば再生ボタンが自力で再描画するところだが、
			// セルのレンダラーが描く再生ボタンには効かないようなので、セルを突っついて再表示させる。
			fireTableCellUpdated(indexOfSequenceOnSequencer(), Column.PLAY.ordinal());
		}
	}
	/**
	 * シーケンスリスト
	 */
	private List<SequenceTrackListTableModel> sequenceModelList = new Vector<>();
	/**
	 * このプレイリストが保持している {@link SequenceTrackListTableModel} のリストを返します。
	 */
	public List<SequenceTrackListTableModel> getSequenceModelList() {
		return sequenceModelList;
	}
	/**
	 * 行が選択されているときだけイネーブルになるアクション
	 */
	public abstract class SelectedSequenceAction extends AbstractAction implements ListSelectionListener {
		public SelectedSequenceAction(String name, Icon icon, String tooltip) {
			super(name,icon); init(tooltip);
		}
		public SelectedSequenceAction(String name, String tooltip) {
			super(name); init(tooltip);
		}
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if( e.getValueIsAdjusting() ) return;
			setEnebledBySelection();
		}
		protected void setEnebledBySelection() {
			int index = sequenceListSelectionModel.getMinSelectionIndex();
			setEnabled(index >= 0);
		}
		private void init(String tooltip) {
			putValue(Action.SHORT_DESCRIPTION, tooltip);
			sequenceListSelectionModel.addListSelectionListener(this);
			setEnebledBySelection();
		}
	}
	/**
	 * 繰り返し再生ON/OFF切り替えアクション
	 */
	public Action getToggleRepeatAction() { return toggleRepeatAction; }
	private Action toggleRepeatAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Repeat - 繰り返し再生");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.REPEAT_ICON));
			putValue(SELECTED_KEY, false);
		}
		@Override
		public void actionPerformed(ActionEvent event) { }
	};
	/**
	 * 曲の先頭または前の曲へ戻るアクション
	 */
	public Action getMoveToTopAction() { return moveToTopAction; }
	private Action moveToTopAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION,
				"Move to top or previous song - 曲の先頭または前の曲へ戻る"
			);
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.TOP_ICON));
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			if( sequencerModel.getSequencer().getTickPosition() <= 40 ) {
				try {
					loadNext(-1);
				} catch (InvalidMidiDataException e) {
					throw new RuntimeException("Could not play previous sequence",e);
				}
			}
			sequencerModel.setValue(0);
		}
	};
	/**
	 * 次の曲へ進むアクション
	 */
	public Action getMoveToBottomAction() { return moveToBottomAction; }
	private Action moveToBottomAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Move to next song - 次の曲へ進む");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BOTTOM_ICON));
		}
		public void actionPerformed(ActionEvent event) {
			try {
				if(loadNext(1)) sequencerModel.setValue(0);
			} catch (InvalidMidiDataException e) {
				throw new RuntimeException("Could not play next sequence",e);
			}
		}
	};
	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** MIDIシーケンスの番号 */
		NUMBER("No.", Integer.class, 20),
		/** 再生ボタン */
		PLAY("Play/Stop", String.class, 60) {
			@Override
			public boolean isCellEditable() { return true; }
		},
		/** 再生中の時間位置（分：秒） */
		POSITION("Position", String.class, 60) {
			@Override
			public boolean isCellEditable() { return true; } // ダブルクリックだけ有効
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				if( ! sequenceModel.isOnSequencer() ) return "";
				return sequenceModel.getParent().mmssPosition;
			}
		},
		/** シーケンスの時間長（分：秒） */
		LENGTH("Length", String.class, 80) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				int sec = (int)( sequenceModel.getMicrosecondLength() / 1000L / 1000L );
				return String.format( "%02d:%02d", sec/60, sec%60 );
			}
		},
		/** ファイル名 */
		FILENAME("Filename", String.class, 100) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				String filename = sequenceModel.getFilename();
				return filename == null ? "" : filename;
			}
		},
		/** 変更済みフラグ */
		MODIFIED("Modified", Boolean.class, 50) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.isModified();
			}
		},
		/** シーケンス名（最初のトラックの名前） */
		NAME("Sequence name", String.class, 250) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				String name = sequenceModel.toString();
				return name == null ? "" : name;
			}
		},
		/** 文字コード */
		CHARSET("CharSet", String.class, 80) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.charset;
			}
		},
		/** タイミング解像度 */
		RESOLUTION("Resolution", Integer.class, 60) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.getSequence().getResolution();
			}
		},
		/** トラック数 */
		TRACKS("Tracks", Integer.class, 40) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.getSequence().getTracks().length;
			}
		},
		/** タイミング分割形式 */
		DIVISION_TYPE("DivType", String.class, 50) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				float dt = sequenceModel.getSequence().getDivisionType();
				String dtLabel = divisionTypeLabels.get(dt);
				return dtLabel == null ? "[Unknown]" : dtLabel;
			}
		};
		/** タイミング分割形式に対応するラベル文字列 */
		private static final Map<Float,String> divisionTypeLabels = new HashMap<Float,String>() {
			{
				put(Sequence.PPQ, "PPQ");
				put(Sequence.SMPTE_24, "SMPTE_24");
				put(Sequence.SMPTE_25, "SMPTE_25");
				put(Sequence.SMPTE_30, "SMPTE_30");
				put(Sequence.SMPTE_30DROP, "SMPTE_30DROP");
			}
		};
		String title;
		Class<?> columnClass;
		int preferredWidth;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param columnClass 列のクラス
		 * @param perferredWidth 列の適切な幅
		 */
		private Column(String title, Class<?> columnClass, int preferredWidth) {
			this.title = title;
			this.columnClass = columnClass;
			this.preferredWidth = preferredWidth;
		}
		public boolean isCellEditable() { return false; }
		public Object getValueOf(SequenceTrackListTableModel sequenceModel) { return ""; }
	}

	@Override
	public int getRowCount() { return sequenceModelList.size(); }
	@Override
	public int getColumnCount() { return Column.values().length; }
	@Override
	public String getColumnName(int column) { return Column.values()[column].title; }
	@Override
	public Class<?> getColumnClass(int column) { return Column.values()[column].columnClass; }
	@Override
	public boolean isCellEditable(int row, int column) {
		return Column.values()[column].isCellEditable();
	}
	@Override
	public Object getValueAt(int row, int column) {
		PlaylistTableModel.Column c = Column.values()[column];
		return c == Column.NUMBER ? row : c.getValueOf(sequenceModelList.get(row));
	}
	@Override
	public void setValueAt(Object val, int row, int column) {
		switch(Column.values()[column]) {
		case FILENAME:
			// ファイル名の変更
			sequenceModelList.get(row).setFilename((String)val);
			fireTableCellUpdated(row, column);
			break;
		case NAME:
			// シーケンス名の設定または変更
			if( sequenceModelList.get(row).setName((String)val) )
				fireTableCellUpdated(row, Column.MODIFIED.ordinal());
			fireTableCellUpdated(row, column);
			break;
		case CHARSET:
			// 文字コードの変更
			SequenceTrackListTableModel seq = sequenceModelList.get(row);
			seq.charset = Charset.forName(val.toString());
			fireTableCellUpdated(row, column);
			// シーケンス名の表示更新
			fireTableCellUpdated(row, Column.NAME.ordinal());
			// トラック名の表示更新
			seq.fireTableDataChanged();
		default:
			break;
		}
	}
	/**
	 * このプレイリストに読み込まれた全シーケンスの合計時間長を返します。
	 * @return 全シーケンスの合計時間長 [秒]
	 */
	public int getSecondLength() {
		// マイクロ秒単位での桁あふれを回避しつつ、丸め誤差を最小限にするため、ミリ秒単位で合計を算出する。
		return (int)(sequenceModelList.stream().mapToLong(m -> m.getMicrosecondLength() / 1000L).sum() / 1000L);
	}
	/**
	 * 選択されたMIDIシーケンスのテーブルモデルを返します。
	 * @return 選択されたMIDIシーケンスのテーブルモデル（非選択時はnull）
	 */
	public SequenceTrackListTableModel getSelectedSequenceModel() {
		if( sequenceListSelectionModel.isSelectionEmpty() ) return null;
		int selectedIndex = sequenceListSelectionModel.getMinSelectionIndex();
		if( selectedIndex >= sequenceModelList.size() ) return null;
		return sequenceModelList.get(selectedIndex);
	}
	/**
	 * 指定されたシーケンスの更新済みフラグを変更して全ての列を再表示します。
	 * @param sequenceTableModel MIDIシーケンスモデル
	 * @param isModified 更新済みフラグ
	 */
	public void fireSequenceModified(SequenceTrackListTableModel sequenceTableModel, boolean isModified) {
		int index = sequenceModelList.indexOf(sequenceTableModel);
		if( index < 0 ) return;
		sequenceTableModel.setModified(isModified);
		fireTableRowsUpdated(index, index);
	}
	/**
	 * 指定されている選択範囲のシーケンスについて、更新済みフラグを変更して全ての列を再表示します。
	 * @param isModified 更新済みフラグ
	 */
	public void fireSelectedSequenceModified(boolean isModified) {
		if( sequenceListSelectionModel.isSelectionEmpty() ) return;
		int minIndex = sequenceListSelectionModel.getMinSelectionIndex();
		int maxIndex = sequenceListSelectionModel.getMaxSelectionIndex();
		for( int index = minIndex; index <= maxIndex; index++ ) {
			sequenceModelList.get(index).setModified(isModified);
		}
		fireTableRowsUpdated(minIndex, maxIndex);
	}
	/**
	 * MIDIシーケンスを追加します。
	 * @param sequence MIDIシーケンス（nullの場合、シーケンスを自動生成して追加）
	 * @param filename ファイル名（nullの場合、ファイル名なし）
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int add(Sequence sequence, String filename) {
		if( sequence == null ) sequence = (new ChordProgression()).toMidiSequence();
		sequenceModelList.add(new SequenceTrackListTableModel(this, sequence, filename));
		//
		// 末尾に追加された行を選択し、再描画
		int lastIndex = sequenceModelList.size() - 1;
		sequenceListSelectionModel.setSelectionInterval(lastIndex, lastIndex);
		fireTableRowsInserted(lastIndex, lastIndex);
		return lastIndex;
	}
	/**
	 * 指定されたMIDIシーケンスをこのプレイリストに追加して再生します。
	 * @param sequence MIDIシーケンス
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public int play(Sequence sequence) throws InvalidMidiDataException {
		int lastIndex = add(sequence,"");
		if( ! sequencerModel.getSequencer().isRunning() ) {
			loadToSequencer(lastIndex);
			sequencerModel.start();
		}
		return lastIndex;
	}

	/**
	 * 選択されたシーケンスを除去します。
	 * 除去されたシーケンスがシーケンサーにロード済みの場合、アンロードします。
	 *
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public void removeSelectedSequence() throws InvalidMidiDataException {
		if( sequenceListSelectionModel.isSelectionEmpty() ) return;
		int selectedIndex = sequenceListSelectionModel.getMinSelectionIndex();
		SequenceTrackListTableModel removedSequence = sequenceModelList.remove(selectedIndex);
		fireTableRowsDeleted(selectedIndex, selectedIndex);
		if( removedSequence.isOnSequencer() ) {
			sequencerModel.setSequenceTrackListTableModel(null);
		}
	}
	/**
	 * テーブル内の指定したインデックス位置にあるシーケンスをシーケンサーにロードします。
	 * インデックスに -1 を指定するとアンロードされます。
	 * 変更点がある場合、リスナー（テーブルビュー）に通知します。
	 *
	 * @param newRowIndex ロードするシーケンスのインデックス位置、アンロードするときは -1
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じているときにアンロードしようとした場合
	 */
	public void loadToSequencer(int newRowIndex) throws InvalidMidiDataException {
		SequenceTrackListTableModel oldSeq = sequencerModel.getSequenceTrackListTableModel();
		SequenceTrackListTableModel newSeq = (newRowIndex < 0 || sequenceModelList.isEmpty() ? null : sequenceModelList.get(newRowIndex));
		if( oldSeq == newSeq ) return;
		sequencerModel.setSequenceTrackListTableModel(newSeq);
		int columnIndices[] = {
			Column.PLAY.ordinal(),
			Column.POSITION.ordinal(),
		};
		if( oldSeq != null ) {
			int oldRowIndex = sequenceModelList.indexOf(oldSeq);
			for( int columnIndex : columnIndices ) fireTableCellUpdated(oldRowIndex, columnIndex);
		}
		if( newSeq != null ) {
			for( int columnIndex : columnIndices ) fireTableCellUpdated(newRowIndex, columnIndex);
		}
	}
	/**
	 * 現在シーケンサにロードされているシーケンスのインデックスを返します。
	 * ロードされていない場合は -1 を返します。
	 * @return 現在シーケンサにロードされているシーケンスのインデックス
	 */
	public int indexOfSequenceOnSequencer() {
		return sequenceModelList.indexOf(sequencerModel.getSequenceTrackListTableModel());
	}
	/**
	 * 引数で示された数だけ次へ進めたシーケンスをロードします。
	 * @param offset 進みたいシーケンス数
	 * @return 以前と異なるインデックスのシーケンスをロードできた場合true
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 */
	private boolean loadNext(int offset) throws InvalidMidiDataException {
		int loadedIndex = indexOfSequenceOnSequencer();
		int newIndex = loadedIndex + offset;
		if( newIndex < 0 ) newIndex = 0; else {
			int sz = sequenceModelList.size();
			if( newIndex >= sz ) newIndex = sz - 1;
		}
		if( newIndex == loadedIndex ) return false;
		loadToSequencer(newIndex);
		return true;
	}
}
