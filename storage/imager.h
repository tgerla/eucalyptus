/*
 * imager header file
 */

#ifndef _IMAGER_H_
#define _IMAGER_H_

#include <sys/stat.h> // mode_t
#include "map.h"

struct _imager_command;

typedef struct _imager_param {
	char * key;
	char * val;
} imager_param;

typedef struct _imager_request {
	struct _imager_command * cmd;
	imager_param * params;
	void * internal;
} imager_request;

typedef struct _imager_command {
	char * name;
	char ** (* parameters) (); // returns valid parameter names and info for each
	int (* validate) (imager_request *); // verifies parameters, returning 0 if all is well
	int (* requirements) (imager_request *); // checks on inputs, records outputs
	int (* execute) (imager_request *); // executes the request
	int (* cleanup) (imager_request *);
} imager_command;

// common functions used by commands

void err (const char *format, ...);
void print_req (imager_request * req);
char parse_boolean (const char * s);
char * strduplc (const char * s);
char * parse_loginpassword (char * s);
int verify_readability (const char * path);
char * get_euca_home (void);
map * get_artifacts_map (void);
int ensure_path_exists (const char * path, mode_t mode);
int ensure_dir_exists (const char * path, mode_t mode);

#endif // _IMAGER_H_