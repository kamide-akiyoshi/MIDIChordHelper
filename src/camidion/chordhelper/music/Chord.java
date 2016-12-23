package camidion.chordhelper.music;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.swing.JLabel;

/**
 * 和音（コード - musical chord）のクラス
 */
public class Chord implements Cloneable {
	/**
	 * コード構成音の順序に対応する色
	 */
	public static final Color NOTE_INDEX_COLORS[] = {
		Color.red,
		new Color(0x40,0x40,0xFF),
		Color.orange.darker(),
		new Color(0x20,0x99,0x00),
		Color.magenta,
		Color.orange,
		Color.green
	};
	/**
	 * 音程差の半音オフセットのインデックス
	 */
	public static enum OffsetIndex {
		THIRD,
		FIFTH,
		SEVENTH,
		NINTH,
		ELEVENTH,
		THIRTEENTH
	}
	/**
	 * 音程差
	 */
	public static enum Interval {

		/** 長２度（major 2nd / sus2） */
		SUS2(2, OffsetIndex.THIRD),
		/** 短３度または増２度 */
		MINOR(3, OffsetIndex.THIRD),
		/** 長３度 */
		MAJOR(4, OffsetIndex.THIRD),
		/** 完全４度（parfect 4th / sus4） */
		SUS4(5, OffsetIndex.THIRD),

		/** 減５度または増４度（トライトーン ＝ 三全音 ＝ 半オクターブ） */
		FLAT5(6, OffsetIndex.FIFTH),
		/** 完全５度 */
		PARFECT5(7, OffsetIndex.FIFTH),
		/** 増５度または短６度 */
		SHARP5(8, OffsetIndex.FIFTH),

		/** 長６度または減７度 */
		SIXTH(9, OffsetIndex.SEVENTH),
		/** 短７度 */
		SEVENTH(10, OffsetIndex.SEVENTH),
		/** 長７度 */
		MAJOR_SEVENTH(11, OffsetIndex.SEVENTH),

		/** 短９度（短２度の１オクターブ上） */
		FLAT9(13, OffsetIndex.NINTH),
		/** 長９度（長２度の１オクターブ上） */
		NINTH(14, OffsetIndex.NINTH),
		/** 増９度（増２度の１オクターブ上） */
		SHARP9(15, OffsetIndex.NINTH),

		/** 完全１１度（完全４度の１オクターブ上） */
		ELEVENTH(17, OffsetIndex.ELEVENTH),
		/** 増１１度（増４度の１オクターブ上） */
		SHARP11(18, OffsetIndex.ELEVENTH),

		/** 短１３度（短６度の１オクターブ上） */
		FLAT13(20, OffsetIndex.THIRTEENTH),
		/** 長１３度（長６度の１オクターブ上） */
		THIRTEENTH(21, OffsetIndex.THIRTEENTH);

		private Interval(int chromaticOffset, OffsetIndex offsetIndex) {
			this.chromaticOffset = chromaticOffset;
			this.offsetIndex = offsetIndex;
		}
		private OffsetIndex offsetIndex;
		private int chromaticOffset;
		/**
		 * 半音差を返します。
		 * @return 半音差
		 */
		public int getChromaticOffset() { return chromaticOffset; }
		/**
		 * 対応するインデックスを返します。
		 * @return 対応するインデックス
		 */
		public OffsetIndex getChromaticOffsetIndex() {
			return offsetIndex;
		}
	}
	/**
	 * デフォルトの半音値（メジャーコード固定）
	 */
	public static Map<OffsetIndex, Interval>
		DEFAULT_OFFSETS = new HashMap<OffsetIndex, Interval>() {
			{
				Interval itv;
				itv = Interval.MAJOR; put(itv.getChromaticOffsetIndex(), itv);
				itv = Interval.PARFECT5; put(itv.getChromaticOffsetIndex(), itv);
			}
		};
	/**
	 * 現在有効な構成音の音程（ルート音を除く）
	 */
	public Map<OffsetIndex, Interval> offsets = new HashMap<>(DEFAULT_OFFSETS);
	/**
	 * このコードのルート音
	 */
	private NoteSymbol rootNoteSymbol;
	/**
	 * このコードのベース音（ルート音と異なる場合は分数コードの分母）
	 */
	private NoteSymbol bassNoteSymbol;

	/**
	 * コード C major を構築します。
	 */
	public Chord() {
		this(new NoteSymbol());
	}
	/**
	 * 指定した音名のメジャーコードを構築します。
	 * @param noteSymbol 音名
	 */
	public Chord(NoteSymbol noteSymbol) {
		setRoot(noteSymbol);
		setBass(noteSymbol);
	}
	/**
	 * 指定された調と同名のコードを構築します。
	 * <p>元の調がマイナーキーの場合はマイナーコード、
	 * それ以外の場合はメジャーコードになります。
	 * </p>
	 * @param key 調
	 */
	public Chord(Key key) {
		int keyCo5 = key.toCo5();
		if( key.majorMinor() == Key.MajorMinor.MINOR ) {
			keyCo5 += 3;
			set(Interval.MINOR);
		}
		setRoot(new NoteSymbol(keyCo5));
		setBass(new NoteSymbol(keyCo5));
	}
	/**
	 * コード名の文字列からコードを構築します。
	 * @param chordSymbol コード名の文字列
	 */
	public Chord(String chordSymbol) {
		setChordSymbol(chordSymbol);
	}
	/**
	 * このコードのクローンを作成します。
	 */
	@Override
	public Chord clone() {
		Chord newChord = new Chord(rootNoteSymbol);
		newChord.offsets = new HashMap<>(offsets);
		newChord.setBass(bassNoteSymbol);
		return newChord;
	}
	/**
	 * コードのルート音を指定された音階に置換します。
	 * @param rootNoteSymbol 音階
	 * @return このコード自身（置換後）
	 */
	public Chord setRoot(NoteSymbol rootNoteSymbol) {
		this.rootNoteSymbol = rootNoteSymbol;
		return this;
	}
	/**
	 * コードのベース音を指定された音階に置換します。
	 * @param rootNoteSymbol 音階
	 * @return このコード自身（置換後）
	 */
	public Chord setBass(NoteSymbol rootNoteSymbol) {
		this.bassNoteSymbol = rootNoteSymbol;
		return this;
	}
	/**
	 * コードの種類を設定します。
	 * @param itv 設定する音程
	 */
	public void set(Interval itv) {
		offsets.put(itv.getChromaticOffsetIndex(), itv);
	}
	/**
	 * コードに設定した音程をクリアします。
	 * @param index 半音差インデックス
	 */
	public void clear(OffsetIndex index) {
		offsets.remove(index);
	}
	//
	// コードネームの文字列が示すコードに置き換えます。
	public Chord setChordSymbol(String chordSymbol) {
		//
		// 分数コードの分子と分母に分ける
		String parts[] = chordSymbol.trim().split("(/|on)");
		if( parts.length == 0 ) {
			return this;
		}
		// ルート音とベース音を設定
		setRoot(new NoteSymbol(parts[0]));
		setBass(new NoteSymbol(parts[ parts.length > 1 ? 1 : 0 ]));
		String suffix = parts[0].replaceFirst("^[A-G][#bx]*","");
		//
		// () があれば、その中身を取り出す
		String suffixParts[] = suffix.split("[\\(\\)]");
		if( suffixParts.length == 0 ) {
			return this;
		}
		String suffixParen = "";
		if( suffixParts.length > 1 ) {
			suffixParen = suffixParts[1];
			suffix = suffixParts[0];
		}
		Interval itv;
		//
		// +5 -5 aug dim の判定
		set(
			suffix.matches(".*(\\+5|aug|#5).*") ? Interval.SHARP5 :
			suffix.matches(".*(-5|dim|b5).*") ? Interval.FLAT5 :
			Interval.PARFECT5
		);
		//
		// 6 7 M7 の判定
		itv = suffix.matches(".*(M7|maj7|M9|maj9).*") ? Interval.MAJOR_SEVENTH :
			suffix.matches(".*(6|dim[79]).*") ? Interval.SIXTH :
			suffix.matches(".*7.*") ? Interval.SEVENTH :
			null;
		if(itv==null)
			clear(OffsetIndex.SEVENTH);
		else
			set(itv);
		//
		// マイナーの判定。maj7 と間違えないように比較
		set(
			(suffix.matches(".*m.*") && ! suffix.matches(".*ma.*") ) ? Interval.MINOR :
			suffix.matches(".*sus4.*") ? Interval.SUS4 :
			Interval.MAJOR
		);
		//
		// 9th の判定
		if( suffix.matches(".*9.*") ) {
			set(Interval.NINTH);
			if( ! suffix.matches( ".*(add9|6|M9|maj9|dim9).*") ) {
				set(Interval.SEVENTH);
			}
		}
		else {
			offsets.remove(OffsetIndex.NINTH);
			offsets.remove(OffsetIndex.ELEVENTH);
			offsets.remove(OffsetIndex.THIRTEENTH);
			//
			// () の中を , で分ける
			String parts_in_paren[] = suffixParen.split(",");
			for( String p : parts_in_paren ) {
				if( p.matches("(\\+9|#9)") )
					offsets.put(OffsetIndex.NINTH, Interval.SHARP9);
				else if( p.matches("(-9|b9)") )
					offsets.put(OffsetIndex.NINTH, Interval.FLAT9);
				else if( p.matches("9") )
					offsets.put(OffsetIndex.NINTH, Interval.NINTH);

				if( p.matches("(\\+11|#11)") )
					offsets.put(OffsetIndex.ELEVENTH, Interval.SHARP11);
				else if( p.matches("11") )
					offsets.put(OffsetIndex.ELEVENTH, Interval.ELEVENTH);

				if( p.matches("(-13|b13)") )
					offsets.put(OffsetIndex.THIRTEENTH, Interval.FLAT13);
				else if( p.matches("13") )
					offsets.put(OffsetIndex.THIRTEENTH, Interval.THIRTEENTH);

				// -5 や +5 が () の中にあっても解釈できるようにする
				if( p.matches("(-5|b5)") )
					offsets.put(OffsetIndex.FIFTH, Interval.FLAT5);
				else if( p.matches("(\\+5|#5)") )
					offsets.put(OffsetIndex.FIFTH, Interval.SHARP5);
			}
		}
		return this;
	}
	/**
	 * ルート音を返します。
	 * @return ルート音
	 */
	public NoteSymbol rootNoteSymbol() { return rootNoteSymbol; }
	/**
	 * ベース音を返します。分数コードの場合はルート音と異なります。
	 * @return ベース音
	 */
	public NoteSymbol bassNoteSymbol() { return bassNoteSymbol; }
	/**
	 * 指定した音程が設定されているか調べます。
	 * @param itv 音程
	 * @return 指定した音程が設定されていたらtrue
	 */
	public boolean isSet(Interval itv) {
		return offsets.get(itv.getChromaticOffsetIndex()) == itv;
	}
	/**
	 * 指定したインデックスに音程が設定されているか調べます。
	 * @param index インデックス
	 * @return 指定したインデックスに音程が設定されていたらtrue
	 */
	public boolean isSet(OffsetIndex index) {
		return offsets.containsKey(index);
	}
	/**
	 * コードが等しいかどうかを判定します。
	 * @return 等しければtrue
	 */
	@Override
	public boolean equals(Object anObject) {
		if( this == anObject )
			return true;
		if( anObject instanceof Chord ) {
			Chord another = (Chord) anObject;
			if( ! rootNoteSymbol.equals(another.rootNoteSymbol) )
				return false;
			if( ! bassNoteSymbol.equals(another.bassNoteSymbol) )
				return false;
			return offsets.equals(another.offsets);
		}
		return false;
	}
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	/**
	 * コードが等しいかどうかを、異名同音を無視して判定します。
	 * @param another 比較対象のコード
	 * @return 等しければtrue
	 */
	public boolean equalsEnharmonically(Chord another) {
		if( this == another )
			return true;
		if( another == null )
			return false;
		if( ! rootNoteSymbol.equalsEnharmonically(another.rootNoteSymbol) )
			return false;
		if( ! bassNoteSymbol.equalsEnharmonically(another.bassNoteSymbol) )
			return false;
		return offsets.equals(another.offsets);
	}
	/**
	 * コード構成音の数を返します
	 * （ルート音は含まれますが、ベース音は含まれません）。
	 *
	 * @return コード構成音の数
	 */
	public int numberOfNotes() { return offsets.size() + 1; }
	/**
	 * 指定された位置にあるノート番号を返します。
	 * @param index 位置（０をルート音とした構成音の順序）
	 * @return ノート番号（該当する音がない場合は -1）
	 */
	public int noteAt(int index) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		if( index == 0 )
			return rootnote;
		Interval itv;
		int i=0;
		for( OffsetIndex offsetIndex : OffsetIndex.values() )
			if( (itv = offsets.get(offsetIndex)) != null && ++i == index )
				return rootnote + itv.getChromaticOffset();
		return -1;
	}
	/**
	 * コード構成音を格納したノート番号の配列を返します。
	 * （ベース音は含まれません）
	 * 音域が指定された場合、その音域に合わせたノート番号を返します。
	 * @param range 音域（null可）
	 * @param key キー（null可）
	 * @return ノート番号の配列
	 */
	public int[] toNoteArray(Range range, Key key) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		int ia[] = new int[numberOfNotes()];
		int i;
		ia[i=0] = rootnote;
		Interval itv;
		for( OffsetIndex offsetIndex : OffsetIndex.values() )
			if( (itv = offsets.get(offsetIndex)) != null )
				ia[++i] = rootnote + itv.getChromaticOffset();
		if( range != null )
			range.invertNotesOf(ia, key);
		return ia;
	}
	/**
	 * MIDI ノート番号が、コードの構成音の何番目（０＝ルート音）に
	 * あるかを表すインデックス値を返します。
	 * 構成音に該当しない場合は -1 を返します。
	 * ベース音は検索されません。
	 * @param noteNumber MIDIノート番号
	 * @return 構成音のインデックス値
	 */
	public int indexOf(int noteNumber) {
		int relativeNote = noteNumber - rootNoteSymbol.toNoteNumber();
		if( Music.mod12(relativeNote) == 0 ) return 0;
		Interval itv;
		int i=0;
		for( OffsetIndex offsetIndex : OffsetIndex.values() ) {
			if( (itv = offsets.get(offsetIndex)) != null ) {
				i++;
				if( Music.mod12(relativeNote - itv.getChromaticOffset()) == 0 )
					return i;
			}
		}
		return -1;
	}
	/**
	 * 指定したキーのスケールを外れた構成音がないか調べます。
	 * @param key 調べるキー
	 * @return スケールを外れている構成音がなければtrue
	 */
	public boolean isOnScaleInKey(Key key) {
		return isOnScaleInKey(key.toCo5());
	}
	private boolean isOnScaleInKey(int keyCo5) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		if( ! Music.isOnScale(rootnote, keyCo5) )
			return false;
		Interval itv;
		for( OffsetIndex offsetIndex : OffsetIndex.values() ) {
			if( (itv = offsets.get(offsetIndex)) == null )
				continue;
			if( ! Music.isOnScale(rootnote + itv.getChromaticOffset(), keyCo5) )
				return false;
		}
		return true;
	}
	/**
	 * 移調したコードを返します。
	 * @param chromatic_offset 移調幅（半音単位）
	 * @return 移調した新しいコード（移調幅が０の場合は自分自身）
	 */
	public Chord transpose(int chromatic_offset) {
		return transposedChord(chromatic_offset, 0);
	}
	public Chord transpose(int chromatic_offset, Key original_key) {
		return transposedChord(chromatic_offset, original_key.toCo5());
	}
	private Chord transposedChord(int chromatic_offset, int original_key_co5) {
		if( chromatic_offset == 0 ) return this;
		int offsetCo5 = Music.mod12(Music.reverseCo5(chromatic_offset));
		if( offsetCo5 > 6 ) offsetCo5 -= 12;
		int key_co5   = original_key_co5 + offsetCo5;
		//
		int newRootCo5 = rootNoteSymbol.toCo5() + offsetCo5;
		int newBassCo5 = bassNoteSymbol.toCo5() + offsetCo5;
		if( key_co5 > 6 ) {
			newRootCo5 -= 12;
			newBassCo5 -= 12;
		}
		else if( key_co5 < -5 ) {
			newRootCo5 += 12;
			newBassCo5 += 12;
		}
		setRoot(new NoteSymbol(newRootCo5));
		return setBass(new NoteSymbol(newBassCo5));
	}
	/**
	 * この和音の文字列表現としてコード名を返します。
	 * @return この和音のコード名
	 */
	@Override
	public String toString() {
		String chordSymbol = rootNoteSymbol + symbolSuffix();
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			chordSymbol += "/" + bassNoteSymbol;
		}
		return chordSymbol;
	}
	/**
	 * コード名を HTML で返します。
	 *
	 * Swing の {@link JLabel#setText(String)} は HTML で指定できるので、
	 * 文字の大きさに変化をつけることができます。
	 *
	 * @param colorName 色のHTML表現（色名または #RRGGBB 形式）
	 * @return コード名のHTML
	 */
	public String toHtmlString(String colorName) {
		String span = "<span style=\"font-size: 120%\">";
		String endSpan = "</span>";
		String root = rootNoteSymbol.toString();
		String formattedRoot = (root.length() == 1) ? root + span :
			root.replace("#",span+"<sup>#</sup>").
			replace("b",span+"<sup>b</sup>").
			replace("x",span+"<sup>x</sup>");
		String formattedBass = "";
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			String bass = bassNoteSymbol.toString();
			formattedBass = (bass.length() == 1) ? bass + span :
				bass.replace("#",span+"<sup>#</sup>").
				replace("b",span+"<sup>b</sup>").
				replace("x",span+"<sup>x</sup>");
			formattedBass = "/" + formattedBass + endSpan;
		}
		String suffix = symbolSuffix().
			replace("-5","<sup>-5</sup>").
			replace("+5","<sup>+5</sup>");
		return
			"<html>" +
			"<span style=\"color: " + colorName + "; font-size: 170% ; white-space: nowrap ;\">" +
			formattedRoot + suffix + endSpan + formattedBass +
			"</span>" +
			"</html>" ;
	}
	/**
	 * コードの説明（英語）を返します。
	 * @return コードの説明（英語）
	 */
	public String toName() {
		String name = rootNoteSymbol.toStringIn(NoteSymbol.Language.NAME) + nameSuffix() ;
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			name += " on " + bassNoteSymbol.toStringIn(NoteSymbol.Language.NAME);
		}
		return name;
	}
	/**
	 * コードネームの音名を除いた部分（サフィックス）を組み立てて返します。
	 * @return コードネームの音名を除いた部分
	 */
	public String symbolSuffix() {
		String suffix = (
			offsets.get(OffsetIndex.THIRD) == Interval.MINOR ? "m" : ""
		);
		Interval itv;
		if( (itv = offsets.get(OffsetIndex.SEVENTH)) != null ) {
			switch(itv) {
			case SIXTH:         suffix += "6";  break;
			case SEVENTH:       suffix += "7";  break;
			case MAJOR_SEVENTH: suffix += "M7"; break;
			default: break;
			}
		}
		switch( offsets.get(OffsetIndex.THIRD) ) {
		case SUS4: suffix += "sus4"; break;
		case SUS2: suffix += "sus2"; break;
		default: break;
		}
		switch( offsets.get(OffsetIndex.FIFTH) ) {
		case FLAT5:  suffix += "-5"; break;
		case SHARP5: suffix += "+5"; break;
		default: break;
		}
		Vector<String> paren = new Vector<String>();
		if( (itv = offsets.get(OffsetIndex.NINTH)) != null ) {
			switch(itv) {
			case NINTH:  paren.add("9"); break;
			case FLAT9:  paren.add("-9"); break;
			case SHARP9: paren.add("+9"); break;
			default: break;
			}
		}
		if( (itv = offsets.get(OffsetIndex.ELEVENTH)) != null ) {
			switch(itv) {
			case ELEVENTH: paren.add("11"); break;
			case SHARP11:  paren.add("+11"); break;
			default: break;
			}
		}
		if( (itv = offsets.get(OffsetIndex.THIRTEENTH)) != null ) {
			switch(itv) {
			case THIRTEENTH: paren.add("13"); break;
			case FLAT13:     paren.add("-13"); break;
			default: break;
			}
		}
		if( ! paren.isEmpty() ) {
			boolean is_first = true;
			suffix += "(";
			for( String p : paren ) {
				if( is_first )
					is_first = false;
				else
					suffix += ",";
				suffix += p;
			}
			suffix += ")";
		}
		if( suffix.equals("m-5") ) return "dim";
		else if( suffix.equals("+5") ) return "aug";
		else if( suffix.equals("m6-5") ) return "dim7";
		else if( suffix.equals("(9)") ) return "add9";
		else if( suffix.equals("7(9)") ) return "9";
		else if( suffix.equals("M7(9)") ) return "M9";
		else if( suffix.equals("7+5") ) return "aug7";
		else if( suffix.equals("m6-5(9)") ) return "dim9";
		else return suffix ;
	}
	/**
	 * コードの説明のうち、音名を除いた部分を組み立てて返します。
	 * @return コード説明の音名を除いた部分
	 */
	public String nameSuffix() {
		String suffix = "";
		if( offsets.get(OffsetIndex.THIRD) == Interval.MINOR )
			suffix += " minor";
		Interval itv;
		if( (itv = offsets.get(OffsetIndex.SEVENTH)) != null ) {
			switch(itv) {
			case SIXTH:         suffix += " 6th"; break;
			case SEVENTH:       suffix += " 7th"; break;
			case MAJOR_SEVENTH: suffix += " major 7th"; break;
			default: break;
			}
		}
		switch( offsets.get(OffsetIndex.THIRD) ) {
		case SUS4: suffix += " suspended 4th"; break;
		case SUS2: suffix += " suspended 2nd"; break;
		default: break;
		}
		switch( offsets.get(OffsetIndex.FIFTH) ) {
		case FLAT5 : suffix += " flatted 5th"; break;
		case SHARP5: suffix += " sharped 5th"; break;
		default: break;
		}
		Vector<String> paren = new Vector<String>();
		if( (itv = offsets.get(OffsetIndex.NINTH)) != null ) {
			switch(itv) {
			case NINTH:  paren.add("9th"); break;
			case FLAT9:  paren.add("flatted 9th"); break;
			case SHARP9: paren.add("sharped 9th"); break;
			default: break;
			}
		}
		if( (itv = offsets.get(OffsetIndex.ELEVENTH)) != null ) {
			switch(itv) {
			case ELEVENTH: paren.add("11th"); break;
			case SHARP11:  paren.add("sharped 11th"); break;
			default: break;
			}
		}
		if( (itv = offsets.get(OffsetIndex.THIRTEENTH)) != null ) {
			switch(itv) {
			case THIRTEENTH: paren.add("13th"); break;
			case FLAT13:     paren.add("flatted 13th"); break;
			default: break;
			}
		}
		if( ! paren.isEmpty() ) {
			boolean is_first = true;
			suffix += "(additional ";
			for( String p : paren ) {
				if( is_first )
					is_first = false;
				else
					suffix += ",";
				suffix += p;
			}
			suffix += ")";
		}
		if( suffix.equals(" minor flatted 5th") ) return " diminished (triad)";
		else if( suffix.equals(" sharped 5th") ) return " augumented";
		else if( suffix.equals(" minor 6th flatted 5th") ) return " diminished 7th";
		else if( suffix.equals(" 7th(additional 9th)") ) return " 9th";
		else if( suffix.equals(" major 7th(additional 9th)") ) return " major 9th";
		else if( suffix.equals(" 7th sharped 5th") ) return " augumented 7th";
		else if( suffix.equals(" minor 6th flatted 5th(additional 9th)") ) return " diminished 9th";
		else if( suffix.isEmpty() ) return " major";
		else return suffix ;
	}
}