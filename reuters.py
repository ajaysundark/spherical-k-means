#!/usr/bin/python

import os
import fnmatch
import operator
from pprint import pprint
from BeautifulSoup import BeautifulSoup

class DataStore():
	def __init__(self):
		self.topLabels = []
		self.topArticleswLabels = {}
		self.topArticleContents = {}
		self.bagofwords = {}
		self.clabel_bag = []
		self.bag_freq = {}
		self.clabel_ngrams = {}

		self.freq_3grams = {}
		self.freq_5grams = {}
		self.freq_7grams = {}
		self.fout_nglabel = None

		self.lablFreq = {}
		self.lablArticles = {}
		self.articleBody = {}

	def createNGramsFreq(self, body, window):
		freq_vector = {}
		for startx in range(len(body)-window+1):
			tempstr = body[startx:startx+window]
			freq_vector.setdefault(tempstr, 0)
			freq_vector[tempstr]+=1

		for key in freq_vector.keys():
			if window==3:
				self.freq_3grams.setdefault(key, 0)
				self.freq_3grams[key]+=freq_vector[key]
			elif window==5:
				self.freq_5grams.setdefault(key, 0)
				self.freq_5grams[key]+=freq_vector[key]
			else:
				self.freq_7grams.setdefault(key, 0)
				self.freq_7grams[key]+=freq_vector[key]

	def beautify(self, body, article):
		mybag = []
		freq_vector= {}
		word = []
		msg = body.lower()
		for ch in msg:
			if ord(ch)>128:
				continue
			elif ch.isalnum():
				word.append(ch)
			# elif ch.isspace():
			else:
				if len(word)>0:
					temp = ''.join(word)
					if temp.isdigit():
						pass
					else:
						mybag.append(temp)
						freq_vector.setdefault(temp,0)
						freq_vector[temp]+=1

						if temp not in self.clabel_bag:
							self.clabel_bag.append(temp)
							# idx = self.clabel_bag.index(temp)
							# self.fout_blabel.write(str(idx) + ',' + temp + '\n')
					del word[:]
				else:
					continue
			# else:
				# continue

		newmsg = " ".join(mybag)

		for key in freq_vector.keys():
			self.bag_freq.setdefault(key, 0)
			self.bag_freq[key]+=freq_vector[key]

		self.bagofwords[article] = freq_vector
		return newmsg

	def createBagOfWordsCSV(self):
		fout_bcsv = open("bag.csv", "w")
		fout_blabel = open("bag.clabel", "w")
		written = []
		# count = 0
		for article in self.topArticleswLabels.keys():
			mybag = self.bagofwords[article]
			# print 
			# print 'article vs labels :', article
			# print self.topArticleContents[article]
			# print '\nits bag'
			# pprint(mybag)
			# print 
			for temp in mybag:
				freq = self.bag_freq[temp]
				if freq>5:
					idx = str(self.clabel_bag.index(temp))
					if temp not in written:
						written.append(temp)
						fout_blabel.write(idx+','+temp+'\n')
						# print 'adding to freq word label : ' + idx+','+temp

					fout_bcsv.write(str(article)+','+idx+','+str(mybag[temp])+'\n')
					# print 'adding ',temp,': (', str(article)+','+idx+','+str(mybag[temp]), ') which has total frequency :', freq

			# if count>2:
				# break
			# else: count+=1
		fout_blabel.close()
		fout_bcsv.close()

	def generateNGrams(self, window, articleid):
		mybag = {}
		self.clabel_ngrams.setdefault(window, [])
		# self.ngrams_freq.setdefault(window, [])

		body = self.topArticleContents[articleid]
		# print body
		# print

		for startx in range(len(body)-window+1):
			tempstr = body[startx:startx+window]
			mybag.setdefault(tempstr, 0)
			mybag[tempstr]+=1

		return mybag

	def createNGramsCSV(self):
		for n in [3,5,7]:
			fout_ngcsv = open('char'+str(n)+'.csv', "w")
			self.fout_nglabel = open('char'+str(n)+'.clabel', "w")
			for article in self.topArticleswLabels.keys():
				mybag = self.generateNGrams(n, article)
				# print mybag
				# print
				for temp in mybag:
					if n==3 and (self.freq_3grams[temp]<5):
						continue
					elif n==5 and (self.freq_5grams[temp]<5):
						continue
					elif  n==7 and (self.freq_7grams[temp]<5):
						continue
					else:
						if temp not in self.clabel_ngrams[n]:
							self.clabel_ngrams[n].append(temp)
							self.fout_nglabel.write(str(self.clabel_ngrams[n].index(temp)) + ',' + temp + '\n')
						fout_ngcsv.write(str(article)+','+str(self.clabel_ngrams[n].index(temp))+','+str(mybag[temp])+'\n')
			self.fout_nglabel.close()
			fout_ngcsv.close()

	def findTop(self):
		sortedSingles = sorted(self.lablFreq.items(), key=operator.itemgetter(1), reverse=True)
		# pprint(sortedSingles[:20])
		for aTuple in sortedSingles[:20]:
			self.topLabels.append(aTuple[0])
		# print
		# print 'Top labels :', self.topLabels

		for label in self.topLabels:
			articles = self.lablArticles.get(label)
			for articleid in articles:
				self.topArticleswLabels[articleid] = label

		with open('reuters21578.class', 'w') as fclass:
			for k,v in self.topArticleswLabels.items():
				fclass.write(str(k)+','+str(v)+'\n')

		# print
		# print 'Article# Label#'
		# pprint(self.topArticleswLabels)
		# pprint(self.articleBody.keys())
		for article in self.topArticleswLabels.keys():
			message = self.articleBody[article]
			newmsg = self.beautify(message, article)
			# print message
			# print newmsg
			for n in [3,5,7]:
				self.createNGramsFreq(newmsg, n)
			self.topArticleContents[article] = newmsg

		self.cleanupRaw()
		self.createBagOfWordsCSV()
		self.cleanupBag()
		self.createNGramsCSV()

	def cleanupRaw(self):
		self.lablFreq = None
		self.lablArticles = None
		self.articleBody = None

	def cleanupBag(self):
		self.bagofwords = None
		self.clabel_bag = None
		self.bag_freq = None

class Reuters_Parser():
	def __init__(self, document_path, ds):
		self.path = document_path
		self.store = ds

	def parse(self):
		with open(self.path, 'r') as fhandle:
			soup = BeautifulSoup(fhandle)
			noOfArticles = 0
			for eachArticle in soup('reuters', topics='YES'):
				if eachArticle.topics != None and len(eachArticle.topics.contents)==1:
					if eachArticle.body == None:
						pass
					else:
						label = eachArticle.topics.d.string

						self.store.lablFreq.setdefault(label, 0)
						self.store.lablFreq[label] += 1

						self.store.lablArticles.setdefault(label, [])
						self.store.lablArticles[label].append(int(eachArticle['newid']))
						# print 'article id is', eachArticle['newid'], 'with label ', label

						self.store.articleBody[int(eachArticle['newid'])] = eachArticle.body.string

					# pprint(self.store.articleBody[:2])
					# print self.lablArticles
					noOfArticles+=1
					# break

			print 'single topics found in file (', self.path ,') is :',noOfArticles

			return noOfArticles

class mySGMReader():
	def __init__(self, dataset_path):
		self.dataset_path = dataset_path
		if not os.path.exists(self.dataset_path):
			raise ValueError('The folder ' + self.dataset_path + ' doesnot exist')

	def populateStore(self):
		""" get every file in the folder with *.sgm extension """
		ds = DataStore()
		tot = 0
		for root, dirs, files in os.walk(self.dataset_path):
			for doc in fnmatch.filter(files, '*.sgm'):
				path = os.path.join(root, doc)
				# print(path)
				tot+=1
				Reuters_Parser(path, ds).parse()

		print 'Total Files parsed = ' + str(tot)
		print
		return ds

def main():
	datastore = mySGMReader("reuters21578").populateStore()
	datastore.findTop()
	print 'Pre-processing completed'

if __name__ == '__main__':
	main()
