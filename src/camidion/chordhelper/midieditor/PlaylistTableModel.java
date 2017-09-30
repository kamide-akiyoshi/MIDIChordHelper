package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.IntStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.music.MIDISpec;

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
	public final SequenceTrackListTableModel emptyTrackListTableModel = new SequenceTrackListTableModel(this, null, null, null);
	/**
	 * 空のイベントリストモデル
	 */
	public final MidiEventTableModel emptyEventListTableModel = new MidiEventTableModel(emptyTrackListTableModel, null);
	/** 再生中のシーケンサーの秒位置リスナー */
	private ChangeListener mmssPosition = new ChangeListener() {
		private int value = 0;
		@Override
		public void stateChanged(ChangeEvent event) {
			Object src = event.getSource();
			if( ! (src instanceof MidiSequencerModel) ) return;
			MidiSequencerModel sequencerModel = (MidiSequencerModel)src;
			int newValue = sequencerModel.getValue() / 1000;
			if(value == newValue) return;
			value = newValue;
			fireTableCellUpdated(sequencerModel.getSequenceTrackListTableModel(), Column.POSITION);
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
			if(msg.getType() == 0x2F) SwingUtilities.invokeLater(()->goNext());
		});
	}
	/**
	 * 次の曲へ進みます。
	 * <p>リピートモードの場合は同じ曲をもう一度再生、そうでない場合は次の曲へ進んで再生します。
	 * 次の曲がなければ、そこで停止します。いずれの場合も曲の先頭へ戻ります。
	 * </p>
	 * @throws IllegalStateException {@link #loadNext(int)} から
	 * {@link InvalidMidiDataException} がスローされた場合
	 * （MIDIシーケンサデバイスが閉じている状態で呼び出されたことが主な原因）
	 */
	private void goNext() {
		// とりあえず曲の先頭へ戻る
		sequencerModel.getSequencer().setMicrosecondPosition(0);
		try {
			if( (Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY) || loadNext(1) ) {
				// リピートモードのときはもう一度同じ曲を、そうでない場合は次の曲を再生開始
				sequencerModel.start();
			}
			else {
				// 最後の曲が終わったので、停止状態にする
				sequencerModel.stop();
				// ここでボタンが停止状態に変わったはずなので、通常であれば再生ボタンが自力で再描画するところだが、
				// セルのレンダラーが描く再生ボタンには効かないようなので、セルを突っついて再表示させる。
				fireTableCellUpdated(sequencerModel.getSequenceTrackListTableModel(), Column.PLAY);
			}
		} catch (InvalidMidiDataException ex) {
			throw new IllegalStateException("Could not play next sequence after end-of-track",ex);
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
		NUMBER("#", Integer.class, 20),
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
				return sequenceModel.toString();
			}
		},
		/** 文字コード */
		CHARSET("CharSet", String.class, 80) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.getCharset();
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
			private Map<Float,String> labels;
			{
				Map<Float,String> m = new HashMap<Float,String>();
				m.put(Sequence.PPQ, "PPQ");
				m.put(Sequence.SMPTE_24, "SMPTE_24");
				m.put(Sequence.SMPTE_25, "SMPTE_25");
				m.put(Sequence.SMPTE_30, "SMPTE_30");
				m.put(Sequence.SMPTE_30DROP, "SMPTE_30DROP");
				labels = Collections.unmodifiableMap(m);
			}
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				String label = labels.get(sequenceModel.getSequence().getDivisionType());
				return label == null ? "[Unknown]" : label;
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
		/**
		 * この列が変更されたか調べます。
		 * @param event テーブルモデルの変更を示すイベント
		 * @return　この列に変更がある場合true
		 */
		public boolean isChanged(TableModelEvent event) {
			int index = event.getColumn();
			return index == ordinal() || index == TableModelEvent.ALL_COLUMNS ;
		}
	}
	/**
	 * 連携中のシーケンサにロードされているシーケンスの行の、指定された列が変更されたか調べます。
	 * ロードされているシーケンスがない場合、変更なしとみなされます。
	 * @param event テーブルモデルの変更を示すイベント
	 * @param column 対象の列（nullを指定すると、どの列が変更されても、その行の変更だけで変更ありとみなされる）
	 * @return 変更がある場合true
	 */
	public boolean isLoadedSequenceChanged(TableModelEvent event, Column column) {
		if( column != null && ! column.isChanged(event) ) return false;
		SequenceTrackListTableModel loadedSequence = sequencerModel.getSequenceTrackListTableModel();
		return loadedSequence != null && IntStream.rangeClosed(event.getFirstRow(), event.getLastRow())
			.anyMatch( index -> index != TableModelEvent.HEADER_ROW && sequenceModelList.get(index) == loadedSequence );
	}
	/**
	 * [row, column]にあるセルの値が更新されたことを、すべてのリスナーに通知します。
	 * @param row 更新されたセルの行
	 * @param column 更新されたセルの列
	 * @see #fireTableCellUpdated(int, int)
	 */
	public void fireTableCellUpdated(int row, Column column) {
		fireTableCellUpdated(row, column.ordinal());
	}
	/**
	 * [sequence, column]にあるセルの値が更新されたことを、すべてのリスナーに通知します。
	 * @param sequence 更新されたMIDIシーケンス
	 * @param column 更新されたセルの列
	 * @see #fireTableCellUpdated(int, int)
	 */
	public void fireTableCellUpdated(SequenceTrackListTableModel sequence, Column column) {
		fireTableCellUpdated(sequenceModelList.indexOf(sequence), column);
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
			sequenceModelList.get(row).setFilename(val.toString());
			fireTableCellUpdated(row, column);
			break;
		case NAME:
			// シーケンス名の設定または変更
			if( sequenceModelList.get(row).setName(val.toString()) )
				fireTableCellUpdated(row, Column.MODIFIED);
			fireTableCellUpdated(row, column);
			break;
		case CHARSET:
			// 文字コードの変更
			SequenceTrackListTableModel seq = sequenceModelList.get(row);
			seq.setCharset(Charset.forName(val.toString()));
			fireTableCellUpdated(row, column);
			// シーケンス名の表示更新
			fireTableCellUpdated(row, Column.NAME);
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
	 * ファイル名なしでMIDIシーケンスを追加します。
	 * 文字コードは自動的に判別されます（判別に失敗した場合はデフォルトの文字コードが指定されます）。
	 * @param sequence MIDIシーケンス
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int add(Sequence sequence) {
		return add(sequence, (String)null);
	}
	/**
	 * ファイル名を指定してMIDIシーケンスを追加します。
	 * 文字コードは自動的に判別されます（判別に失敗した場合はデフォルトの文字コードが指定されます）。
	 * @param sequence MIDIシーケンス
	 * @param filename ファイル名（nullの場合、ファイル名なし）
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int add(Sequence sequence, String filename) {
		Charset charset = MIDISpec.getCharsetOf(sequence);
		if( charset == null ) charset = Charset.defaultCharset();
		return add(sequence, charset, filename);
	}
	/**
	 * ファイル名なしで、文字コードを指定してMIDIシーケンスを追加します。
	 * @param sequence MIDIシーケンス
	 * @param charset MIDIシーケンス内のテキスト文字コード
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int add(Sequence sequence, Charset charset) {
		return add(sequence, charset, null);
	}
	/**
	 * ファイル名、文字コードを指定してMIDIシーケンスを追加します。
	 * @param sequence MIDIシーケンス
	 * @param charset MIDIシーケンス内のテキスト文字コード
	 * @param filename ファイル名（nullの場合、ファイル名なし）
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int add(Sequence sequence, Charset charset, String filename) {
		//if( sequence == null ) {
		//	sequence = (new ChordProgression()).toMidiSequence(charset);
		//}
		SequenceTrackListTableModel sequenceModel =
				new SequenceTrackListTableModel(this, sequence, charset, filename);
		int newIndex = sequenceModelList.size();
		sequenceModelList.add(sequenceModel);
		fireTableRowsInserted(newIndex, newIndex);
		return newIndex;
	}
	/**
	 * MIDIシーケンスを除去します。除去されたMIDIシーケンスがシーケンサーにロード済みだった場合、アンロードします。
	 * @param rowIndex 除去するMIDIシーケンスのインデックス（先頭が 0）
	 * @return 除去されたMIDIシーケンス
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public SequenceTrackListTableModel remove(int rowIndex) throws InvalidMidiDataException {
		SequenceTrackListTableModel removedSequence = sequenceModelList.remove(rowIndex);
		fireTableRowsDeleted(rowIndex, rowIndex);
		if(removedSequence.isOnSequencer()) sequencerModel.setSequenceTrackListTableModel(null);
		return removedSequence;
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
		if( ! sequencerModel.setSequenceTrackListTableModel(newSeq) ) return;
		if( oldSeq != null ) {
			fireTableCellUpdated(oldSeq, Column.PLAY);
			fireTableCellUpdated(oldSeq, Column.POSITION);
		}
		if( newSeq != null ) {
			fireTableCellUpdated(newRowIndex, Column.PLAY);
			fireTableCellUpdated(newRowIndex, Column.POSITION);
		}
	}
	/**
	 * 指定されたインデックスのMIDIシーケンスを再生します。
	 * @param index MIDIシーケンスのインデックス（先頭が 0）
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public void play(int index) throws InvalidMidiDataException {
		loadToSequencer(index);
		sequencerModel.start();
	}
	/**
	 * 指定されたMIDIシーケンスをこのプレイリストに追加し、再生されていなければ追加した曲から再生します。
	 * @param sequence MIDIシーケンス
	 * @param charset 文字コード
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public int play(Sequence sequence, Charset charset) throws InvalidMidiDataException {
		int lastIndex = add(sequence, charset);
		if( ! sequencerModel.getSequencer().isRunning() ) play(lastIndex);
		return lastIndex;
	}
	/**
	 * 引数で示された数だけ次へ進めたシーケンスをロードします。
	 * @param offset 進みたいシーケンス数
	 * @return 以前と異なるインデックスのシーケンスをロードできた場合true
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 */
	private boolean loadNext(int offset) throws InvalidMidiDataException {
		int loadedIndex = sequenceModelList.indexOf(sequencerModel.getSequenceTrackListTableModel());
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
