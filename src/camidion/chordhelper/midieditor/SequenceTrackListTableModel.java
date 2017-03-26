package camidion.chordhelper.midieditor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.music.MIDISpec;

/**
 * MIDIシーケンス（トラックリスト）のテーブルデータモデル
 */
public class SequenceTrackListTableModel extends AbstractTableModel {
	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** トラック番号 */
		TRACK_NUMBER("No.", Integer.class, 20),
		/** イベント数 */
		EVENTS("Events", Integer.class, 40),
		/** Mute */
		MUTE("Mute", Boolean.class, 30),
		/** Solo */
		SOLO("Solo", Boolean.class, 30),
		/** 録音するMIDIチャンネル */
		RECORD_CHANNEL("RecCh", String.class, 40),
		/** MIDIチャンネル */
		CHANNEL("Ch", String.class, 30),
		/** トラック名 */
		TRACK_NAME("Track name", String.class, 100);
		String title;
		Class<?> columnClass;
		int preferredWidth;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param widthRatio 幅の割合
		 * @param columnClass 列のクラス
		 * @param perferredWidth 列の適切な幅
		 */
		private Column(String title, Class<?> columnClass, int preferredWidth) {
			this.title = title;
			this.columnClass = columnClass;
			this.preferredWidth = preferredWidth;
		}
	}
	private PlaylistTableModel sequenceListTableModel;
	/**
	 * このモデルを収容している親のプレイリストを返します。
	 */
	public PlaylistTableModel getParent() { return sequenceListTableModel; }
	/**
	 * ラップされたMIDIシーケンス
	 */
	private Sequence sequence;
	/**
	 * ラップされたMIDIシーケンスのtickインデックス
	 */
	private SequenceTickIndex sequenceTickIndex;
	/**
	 * MIDIファイル名
	 */
	private String filename = "";
	/**
	 * テキスト部分の文字コード（タイトル、歌詞など）
	 */
	public Charset charset = Charset.defaultCharset();
	/**
	 * トラックリスト
	 */
	private List<TrackEventListTableModel> trackModelList = new ArrayList<>();
	private ListSelectionModel trackListSelectionModel = new DefaultListSelectionModel(){
		{
			setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		}
	};
	/**
	 * 選択状態を返します。
	 */
	public ListSelectionModel getSelectionModel() { return trackListSelectionModel; }
	/**
	 * MIDIシーケンスとファイル名から {@link SequenceTrackListTableModel} を構築します。
	 * @param sequenceListTableModel 親のプレイリスト
	 * @param sequence MIDIシーケンス
	 * @param filename ファイル名
	 */
	public SequenceTrackListTableModel(
		PlaylistTableModel sequenceListTableModel,
		Sequence sequence,
		String filename
	) {
		this.sequenceListTableModel = sequenceListTableModel;
		setSequence(sequence);
		setFilename(filename);
	}
	@Override
	public int getRowCount() {
		return sequence == null ? 0 : sequence.getTracks().length;
	}
	@Override
	public int getColumnCount() {
		return Column.values().length;
	}
	/**
	 * 列名を返します。
	 * @return 列名
	 */
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	/**
	 * 指定された列の型を返します。
	 * @return 指定された列の型
	 */
	@Override
	public Class<?> getColumnClass(int column) {
		SequenceTrackListTableModel.Column c = Column.values()[column];
		switch(c) {
		case MUTE:
		case SOLO: if( ! isOnSequencer() ) return String.class;
			// FALLTHROUGH
		default: return c.columnClass;
		}
	}
	@Override
	public Object getValueAt(int row, int column) {
		SequenceTrackListTableModel.Column c = Column.values()[column];
		switch(c) {
		case TRACK_NUMBER: return row;
		case EVENTS: return sequence.getTracks()[row].size();
		case MUTE:
			return isOnSequencer() ? sequenceListTableModel.getSequencerModel().getSequencer().getTrackMute(row) : "";
		case SOLO:
			return isOnSequencer() ? sequenceListTableModel.getSequencerModel().getSequencer().getTrackSolo(row) : "";
		case RECORD_CHANNEL:
			return isOnSequencer() ? trackModelList.get(row).getRecordingChannel() : "";
		case CHANNEL: {
			int ch = trackModelList.get(row).getChannel();
			return ch < 0 ? "" : ch + 1 ;
		}
		case TRACK_NAME: return trackModelList.get(row).toString();
		default: return "";
		}
	}
	/**
	 * セルが編集可能かどうかを返します。
	 */
	@Override
	public boolean isCellEditable(int row, int column) {
		SequenceTrackListTableModel.Column c = Column.values()[column];
		switch(c) {
		case MUTE:
		case SOLO:
		case RECORD_CHANNEL: return isOnSequencer();
		case CHANNEL:
		case TRACK_NAME: return true;
		default: return false;
		}
	}
	/**
	 * 列の値を設定します。
	 */
	@Override
	public void setValueAt(Object val, int row, int column) {
		SequenceTrackListTableModel.Column c = Column.values()[column];
		switch(c) {
		case MUTE:
			sequenceListTableModel.getSequencerModel().getSequencer().setTrackMute(row, ((Boolean)val).booleanValue());
			break;
		case SOLO:
			sequenceListTableModel.getSequencerModel().getSequencer().setTrackSolo(row, ((Boolean)val).booleanValue());
			break;
		case RECORD_CHANNEL:
			trackModelList.get(row).setRecordingChannel((String)val);
			break;
		case CHANNEL: {
			Integer ch;
			try {
				ch = new Integer((String)val);
			}
			catch( NumberFormatException e ) {
				ch = -1;
				break;
			}
			if( --ch <= 0 || ch > MIDISpec.MAX_CHANNELS )
				break;
			TrackEventListTableModel trackTableModel = trackModelList.get(row);
			if( ch == trackTableModel.getChannel() ) break;
			trackTableModel.setChannel(ch);
			setModified(true);
			fireTableCellUpdated(row, Column.EVENTS.ordinal());
			break;
		}
		case TRACK_NAME:
			trackModelList.get(row).setString((String)val);
			break;
		default:
			break;
		}
		fireTableCellUpdated(row,column);
	}
	/**
	 * MIDIシーケンスを返します。
	 * @return MIDIシーケンス
	 */
	public Sequence getSequence() { return sequence; }
	/**
	 * MIDIシーケンスのマイクロ秒単位の長さを返します。
	 * 曲が長すぎて {@link Sequence#getMicrosecondLength()} が負数を返してしまった場合の補正も行います。
	 * @return MIDIシーケンスの長さ[マイクロ秒]
	 */
	public long getMicrosecondLength() {
		long usec = sequence.getMicrosecondLength();
		return usec < 0 ? usec += 0x100000000L : usec;
	}
	/**
	 * シーケンスtickインデックスを返します。
	 * @return シーケンスtickインデックス
	 */
	public SequenceTickIndex getSequenceTickIndex() { return sequenceTickIndex; }
	/**
	 * MIDIシーケンスを設定します。
	 * @param sequence MIDIシーケンス（nullを指定するとトラックリストが空になる）
	 */
	private void setSequence(Sequence sequence) {
		//
		// 旧シーケンスの録音モードを解除
		MidiSequencerModel sequencerModel = sequenceListTableModel.getSequencerModel();
		if( sequencerModel != null ) sequencerModel.getSequencer().recordDisable(null);
		//
		// トラックリストをクリア
		int oldSize = trackModelList.size();
		if( oldSize > 0 ) {
			trackModelList.clear();
			fireTableRowsDeleted(0, oldSize-1);
		}
		// 新シーケンスに置き換える
		if( (this.sequence = sequence) == null ) {
			// 新シーケンスがない場合
			sequenceTickIndex = null;
			return;
		}
		// tickインデックスを再構築
		fireTimeSignatureChanged();
		//
		// トラックリストを再構築
		Track tracks[] = sequence.getTracks();
		for(Track track : tracks) {
			trackModelList.add(new TrackEventListTableModel(this, track));
		}
		// 文字コードの判定
		Charset cs = MIDISpec.getCharsetOf(sequence);
		charset = cs==null ? Charset.defaultCharset() : cs;
		//
		// トラックが挿入されたことを通知
		fireTableRowsInserted(0, tracks.length-1);
	}
	/**
	 * 拍子が変更されたとき、シーケンスtickインデックスを再作成します。
	 */
	public void fireTimeSignatureChanged() {
		sequenceTickIndex = new SequenceTickIndex(sequence);
	}
	private boolean isModified = false;
	/**
	 * 変更されたかどうかを返します。
	 * @return 変更済みのときtrue
	 */
	public boolean isModified() { return isModified; }
	/**
	 * 変更されたかどうかを設定します。
	 * @param isModified 変更されたときtrue
	 */
	public void setModified(boolean isModified) { this.isModified = isModified; }
	/**
	 * ファイル名を設定します。
	 * @param filename ファイル名
	 */
	public void setFilename(String filename) { this.filename = filename; }
	/**
	 * ファイル名を返します。
	 * @return ファイル名
	 */
	public String getFilename() { return filename; }
	/**
	 * このシーケンスを表す文字列としてシーケンス名を返します。シーケンス名がない場合は空文字列を返します。
	 */
	@Override
	public String toString() {
		byte b[] = MIDISpec.getNameBytesOf(sequence);
		return b == null ? "" : new String(b, charset);
	}
	/**
	 * シーケンス名を設定します。
	 * @param name シーケンス名
	 * @return 成功したらtrue
	 */
	public boolean setName(String name) {
		if( name.equals(toString()) ) return false;
		if( ! MIDISpec.setNameBytesOf(sequence, name.getBytes(charset)) ) return false;
		setModified(true);
		fireTableDataChanged();
		return true;
	}
	/**
	 * このシーケンスのMIDIデータのバイト列を返します。
	 * @return MIDIデータのバイト列（ない場合はnull）
	 * @throws IOException バイト列の出力に失敗した場合
	 */
	public byte[] getMIDIdata() throws IOException {
		if( sequence == null || sequence.getTracks().length == 0 ) {
			return null;
		}
		try( ByteArrayOutputStream out = new ByteArrayOutputStream() ) {
			MidiSystem.write(sequence, 1, out);
			return out.toByteArray();
		} catch ( IOException e ) {
			throw e;
		}
	}
	/**
	 * 指定のトラックが変更されたことを通知します。
	 * @param track トラック
	 */
	public void fireTrackChanged(Track track) {
		int row = indexOf(track);
		if( row < 0 ) return;
		fireTableRowsUpdated(row, row);
		sequenceListTableModel.fireSequenceModified(this, true);
	}
	/**
	 * 選択されているトラックモデルを返します。
	 * @param index トラックのインデックス
	 * @return トラックモデル（見つからない場合null）
	 */
	public TrackEventListTableModel getSelectedTrackModel() {
		if( trackListSelectionModel.isSelectionEmpty() )
			return null;
		int index = trackListSelectionModel.getMinSelectionIndex();
		Track tracks[] = sequence.getTracks();
		if( tracks.length != 0 ) {
			Track track = tracks[index];
			for( TrackEventListTableModel model : trackModelList )
				if( model.getTrack() == track )
					return model;
		}
		return null;
	}
	/**
	 * 指定のトラックがある位置のインデックスを返します。
	 * @param track トラック
	 * @return トラックのインデックス（先頭 0、トラックが見つからない場合 -1）
	 */
	public int indexOf(Track track) {
		Track tracks[] = sequence.getTracks();
		for( int i=0; i<tracks.length; i++ )
			if( tracks[i] == track )
				return i;
		return -1;
	}
	/**
	 * 新しいトラックを生成し、末尾に追加します。
	 * @return 追加したトラックのインデックス（先頭 0）
	 */
	public int createTrack() {
		Track newTrack = sequence.createTrack();
		trackModelList.add(new TrackEventListTableModel(this, newTrack));
		int lastRow = getRowCount() - 1;
		fireTableRowsInserted(lastRow, lastRow);
		sequenceListTableModel.fireSelectedSequenceModified(true);
		trackListSelectionModel.setSelectionInterval(lastRow, lastRow);
		return lastRow;
	}
	/**
	 * 選択されているトラックを削除します。
	 */
	public void deleteSelectedTracks() {
		if( trackListSelectionModel.isSelectionEmpty() )
			return;
		int minIndex = trackListSelectionModel.getMinSelectionIndex();
		int maxIndex = trackListSelectionModel.getMaxSelectionIndex();
		Track tracks[] = sequence.getTracks();
		for( int i = maxIndex; i >= minIndex; i-- ) {
			if( ! trackListSelectionModel.isSelectedIndex(i) )
				continue;
			sequence.deleteTrack(tracks[i]);
			trackModelList.remove(i);
		}
		fireTableRowsDeleted(minIndex, maxIndex);
		sequenceListTableModel.fireSelectedSequenceModified(true);
	}
	/**
	 * このシーケンスモデルのシーケンスをシーケンサーが操作しているか調べます。
	 * @return シーケンサーが操作していたらtrue
	 */
	public boolean isOnSequencer() {
		return sequence == sequenceListTableModel.getSequencerModel().getSequencer().getSequence();
	}
	/**
	 * 録音しようとしているチャンネルの設定されたトラックがあるか調べます。
	 * @return 該当トラックがあればtrue
	 */
	public boolean hasRecordChannel() {
		int rowCount = getRowCount();
		for( int row=0; row < rowCount; row++ ) {
			Object value = getValueAt(row, Column.RECORD_CHANNEL.ordinal());
			if( ! "OFF".equals(value) ) return true;
		}
		return false;
	}
}