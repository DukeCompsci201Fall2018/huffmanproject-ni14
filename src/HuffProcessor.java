import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		 }
//
//		out.close();
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			
			if(bits==-1) {
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
				break;
			} else {
				String code = codings[bits];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
		}
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(BITS_PER_WORD + 1, 1 + root.myValue);
		}
		else {
			out.writeBits(BITS_PER_WORD + 1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		// TODO Auto-generated method stub
		
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) {
		// TODO Auto-generated method stub
		if(root.myLeft==null && root.myRight==null) {
			encodings[root.myValue] = path;
			
			if(myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s \n", root.myValue, path);
			}
			return;
		}
		else {
			codingHelper(root.myLeft, path + "0", encodings);
			codingHelper(root.myRight, path + "1", encodings);
		}
		
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		// TODO Auto-generated method stub
		
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i = 0; i < counts.length; i++){
			if(counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}
		
		if(myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq created with %d nodes \n", pq.size());
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
		
		return root;
	}

	/**
	 * Method that determines frequencies of each eight-bit character in the file.
	 * @param in
	 * @return freq - int[] containing frequencies.
	 */
	private int[] readForCounts(BitInputStream in) {
		
		// Initialize new array that contains freqs of all characters and PSEUDO_EOF
		int[] freq = new int[ALPH_SIZE+1];
		
		while (true){
		int val = in.readBits(BITS_PER_WORD);
		if (val == -1) break;
		
		// If val != -1, then increment freq in array by 1.
		else freq[val] = freq[val] + 1;
		
		}
		freq[PSEUDO_EOF] = 1;

		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);

		if(bits != HUFF_TREE){
			throw new HuffException("illegal header starts with " + bits);
		}

		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();


		//while (true){
		//	int val = in.readBits(BITS_PER_WORD);
		//	if (val == -1) break;
		//	out.writeBits(BITS_PER_WORD, val);
		//}


	}


	private HuffNode readTreeHeader(BitInputStream in){

		int bit = in.readBits(1);
		if (bit == -1){
			throw new HuffException("weird");
		}
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}


	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out){

		HuffNode current = root;

		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				
				// Using 0 and 1 to traverse Huffman Tree
				// If 0, go left.
				if (bits == 0) current = current.myLeft;
				
				// If 1, go right.
				else current = current.myRight;

				// If we have arrived at a leaf...
				if (current.myLeft == null && current.myRight == null) {
					
					// Break out of loop if stop character is reached.
					if (current.myValue == PSEUDO_EOF)
						break;   // out of loop
					
					// Otherwise write value of current leaf and restart at root.
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}

	}

	public static void main(String[] args) {
		
	}
	
}